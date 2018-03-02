#include <stdlib.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>
#include <poll.h>
#include <stdio.h>

#include "cutils/log.h"
#include "cutils/memory.h"
#include "cutils/misc.h"
#include "cutils/properties.h"
#include "private/android_filesystem_config.h"
#include <sys/system_properties.h>

#include "libwpa_client/wpa_ctrl.h"

static const char IFACE_DIR[]           = "/data/system/wpa_supplicant";
static const char SUPPLICANT_NAME[]     = "wpa_supplicant";
static const char SUPP_PROP_NAME[]      = "init.svc.wpa_supplicant";
static const char P2P_SUPPLICANT_NAME[] = "p2p_supplicant";
static const char P2P_PROP_NAME[]       = "init.svc.p2p_supplicant";

static const char IFNAME[]              = "IFNAME=";
#define IFNAMELEN           (sizeof(IFNAME) - 1)
static const char WPA_EVENT_IGNORE[]    = "CTRL-EVENT-IGNORE ";

/* Is either SUPPLICANT_NAME or P2P_SUPPLICANT_NAME */
static char supplicant_name[PROPERTY_VALUE_MAX];
/* Is either SUPP_PROP_NAME or P2P_PROP_NAME */
static char supplicant_prop_name[PROPERTY_KEY_MAX];
static struct wpa_ctrl *monitor_conn;
static char primary_iface[PROPERTY_VALUE_MAX];

int support_wifi_scan = 0;
static int on_scan_going = 0;

int wifi_connect_on_socket_path(const char *path)
{
    char supp_status[PROPERTY_VALUE_MAX] = {'\0'};
    
    if (supplicant_name[0] == 0 || supplicant_prop_name[0] == 0) {
        strcpy(supplicant_name, P2P_SUPPLICANT_NAME);
        strcpy(supplicant_prop_name, P2P_PROP_NAME);
    }

    /* Make sure supplicant is running */
    if (!property_get(supplicant_prop_name, supp_status, NULL)
            || strcmp(supp_status, "running") != 0) {
        fprintf(stderr,"Supplicant not running, cannot connect");
        return -1;
    }

    monitor_conn = wpa_ctrl_open(path);
    if (monitor_conn == NULL) {
        return -1;
    }
    if (wpa_ctrl_attach(monitor_conn) != 0) {
        wpa_ctrl_close(monitor_conn);
        monitor_conn = NULL;
        return -1;
    }

    return 0;
}

void wifi_close_sockets()
{
    if (monitor_conn != NULL) {
        wpa_ctrl_close(monitor_conn);
        monitor_conn = NULL;
    }
}

int wifi_supplicant_connection_active()
{
    char supp_status[PROPERTY_VALUE_MAX] = {'\0'};

    if (property_get(supplicant_prop_name, supp_status, NULL)) {
        if (strcmp(supp_status, "stopped") == 0)
            return -1;
    }

    return 0;
}

int wifi_ctrl_recv(char *reply, size_t *reply_len)
{
    int res;
    int ctrlfd = wpa_ctrl_get_fd(monitor_conn);
    struct pollfd rfds[1];

    memset(rfds, 0, sizeof(struct pollfd));
    rfds[0].fd = ctrlfd;
    rfds[0].events |= POLLIN;
    do {
        res = TEMP_FAILURE_RETRY(poll(rfds, 1, 30000));
        if (res < 0) {
            fprintf(stderr,"Error poll = %d", res);
            return res;
        } else if (res == 0) {
            /* timed out, check if supplicant is active
             * or not ..
             */
            res = wifi_supplicant_connection_active();
            if (res < 0)
                return -2;
        }
    } while (res == 0);

    if (rfds[0].revents & POLLIN) {
        /* sometimes monitor_conn maybe NULL, so assert it here */
        if (monitor_conn == NULL) {
            fprintf(stderr,"%s: monitor_conn is NULL\n", __func__);
            return -2;
        }
        return wpa_ctrl_recv(monitor_conn, reply, reply_len);
    }

    /* it is not rfds[0], then it must be rfts[1] (i.e. the exit socket)
     * or we timed out. In either case, this call has failed ..
     */
    return -2;
}

int wifi_wait_on_socket(char *buf, size_t buflen)
{
    size_t nread = buflen - 1;
    int result;
    char *match, *match2;

    if (monitor_conn == NULL) {
        return snprintf(buf, buflen, WPA_EVENT_TERMINATING " - connection closed");
    }

    result = wifi_ctrl_recv(buf, &nread);

    /* Terminate reception on exit socket */
    if (result == -2) {
        return snprintf(buf, buflen, WPA_EVENT_TERMINATING " - connection closed");
    }

    if (result < 0) {
        printf("wifi_ctrl_recv failed: %s\n", strerror(errno));
        return snprintf(buf, buflen, WPA_EVENT_TERMINATING " - recv error");
    }
    buf[nread] = '\0';
    /* Check for EOF on the socket */
    if (result == 0 && nread == 0) {
        /* Fabricate an event to pass up */
        printf("Received EOF on supplicant socket\n");
        return snprintf(buf, buflen, WPA_EVENT_TERMINATING " - signal 0 received");
    }
    /*
     * Events strings are in the format
     *
     *     IFNAME=iface <N>CTRL-EVENT-XXX 
     *        or
     *     <N>CTRL-EVENT-XXX 
     *
     * where N is the message level in numerical form (0=VERBOSE, 1=DEBUG,
     * etc.) and XXX is the event name. The level information is not useful
     * to us, so strip it off.
     */

    if (strncmp(buf, IFNAME, IFNAMELEN) == 0) {
        match = strchr(buf, ' ');
        if (match != NULL) {
            if (match[1] == '<') {
                match2 = strchr(match + 2, '>');
                if (match2 != NULL) {
                    nread -= (match2 - match);
                    memmove(match + 1, match2 + 1, nread - (match - buf) + 1);
                }
            }
        } else {
            return snprintf(buf, buflen, "%s", WPA_EVENT_IGNORE);
        }
    } else if (buf[0] == '<') {
        match = strchr(buf, '>');
        if (match != NULL) {
            nread -= (match + 1 - buf);
            memmove(buf, match + 1, nread + 1);
            printf("supplicant generated event without interface - %s\n", buf);
        }
    } else {
        /* let the event go as is! */
        printf("supplicant generated event without interface and without message level - %s\n", buf);
    }

    return nread;
}


/* Establishes the control and monitor socket connections on the interface */
int wifi_connect_to_supplicant()
{
    static char path[PATH_MAX];

    property_get("wifi.interface", primary_iface, "wlan0");
    if (access(IFACE_DIR, F_OK) == 0) {
        snprintf(path, sizeof(path), "%s/%s", IFACE_DIR, primary_iface);
    } else {
        snprintf(path, sizeof(path), "@android:wpa_%s", primary_iface);
    }
    return wifi_connect_on_socket_path(path);
}

int wifi_on_scangoing()
{
    return on_scan_going;
}

static void *wait_on_event(void *data)
{
#define EVENT_BUF_SIZE 2048
    char buf[EVENT_BUF_SIZE];
    while (1) {
        memset(buf, 0, EVENT_BUF_SIZE);
        int nread = wifi_wait_on_socket(buf, sizeof(buf));
        if (nread <= 0) {
            continue;
        }
        if (strstr(buf, WPA_EVENT_SCAN_STARTED)) {
            on_scan_going = 1;
        } else if (strstr(buf, WPA_EVENT_SCAN_RESULTS)) {
            on_scan_going = 0;
        }
    }

    return NULL;
}

int wifi_scan_monitor()
{
    pthread_attr_t pattr;
    pthread_t thread;
    
    if (wifi_connect_to_supplicant() != 0) {
        fprintf(stderr, "connect supplicant error!\n");
        return -1;
    }

    pthread_attr_init(&pattr);
    pthread_attr_setdetachstate(&pattr, PTHREAD_CREATE_DETACHED);
    if (pthread_create(&thread, &pattr, wait_on_event, NULL)) {
        fprintf(stderr, "pthread create error!\n");
        return -1;
    }

    return 0;
}

