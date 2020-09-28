/******************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#define LOG_TAG "btsnoop"

#ifndef BT_LOG
#define BT_LOG 1
#endif

#include <arpa/inet.h>
#include <assert.h>
#include <ctype.h>
#include <cutils/log.h>
#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <unistd.h>
#include <dirent.h>
#include "bt_hci_bdroid.h"
#include "bt_utils.h"
#include "utils.h"

typedef enum {
  kCommandPacket = 1,
  kAclPacket = 2,
  kScoPacket = 3,
  kEventPacket = 4
} packet_type_t;

// Epoch in microseconds since 01/01/0000.
static const uint64_t BTSNOOP_EPOCH_DELTA = 0x00dcddb30f2f8000ULL;

// Filedescriptor for btsnoop file.
static int hci_btsnoop_fd = -1;

#if BT_LOG
static int bt_log_fd = -1;
#endif
char *buf;

void btsnoop_net_open();
void btsnoop_net_close();
void btsnoop_net_write(const void *data, size_t length);

static uint64_t btsnoop_timestamp(void) {
  struct timeval tv;
  gettimeofday(&tv, NULL);

  // Timestamp is in microseconds.
  uint64_t timestamp = tv.tv_sec * 1000 * 1000LL;
  timestamp += tv.tv_usec;
  timestamp += BTSNOOP_EPOCH_DELTA;
  return timestamp;
}

static void btsnoop_write(const void *data, size_t length) {
  if (hci_btsnoop_fd != -1)
    write(hci_btsnoop_fd, data, length);

  btsnoop_net_write(data, length);
}

static void btsnoop_write_packet(packet_type_t type, const uint8_t *packet, bool is_received) {
  int length_he = 0;
  int length;
  int flags;
  int drops = 0;

  switch (type) {
  case kCommandPacket:
    length_he = packet[2] + 4;
    flags = 2;
    break;
  case kAclPacket:
    length_he = (packet[3] << 8) + packet[2] + 5;
    flags = is_received;
    break;
  case kScoPacket:
    length_he = packet[2] + 4;
    flags = is_received;
    break;
  case kEventPacket:
    length_he = packet[1] + 3;
    flags = 3;
    break;
  }

  uint64_t timestamp = btsnoop_timestamp();
  uint32_t time_hi = timestamp >> 32;
  uint32_t time_lo = timestamp & 0xFFFFFFFF;

  length = htonl(length_he);
  flags = htonl(flags);
  drops = htonl(drops);
  time_hi = htonl(time_hi);
  time_lo = htonl(time_lo);

  // This function is called from different contexts.
  utils_lock();

  btsnoop_write(&length, 4);
  btsnoop_write(&length, 4);
  btsnoop_write(&flags, 4);
  btsnoop_write(&drops, 4);
  btsnoop_write(&time_hi, 4);
  btsnoop_write(&time_lo, 4);
  btsnoop_write(&type, 1);
  btsnoop_write(packet, length_he - 1);

  utils_unlock();
}

void btsnoop_open(const char *p_path, const bool save_existing) {
  assert(p_path != NULL);
  assert(*p_path != '\0');

  btsnoop_net_open();

  if (hci_btsnoop_fd != -1) {
    ALOGE("%s btsnoop log file is already open.", __func__);
    return;
  }

  if (save_existing)
    {
      char fname_backup[266] = {0};
      strncat(fname_backup, p_path, 255);
      strcat(fname_backup, ".last");
      rename(p_path, fname_backup);
    }

  hci_btsnoop_fd = open(p_path,
                        O_WRONLY | O_CREAT | O_TRUNC,
                        S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH);

  if (hci_btsnoop_fd == -1) {
    ALOGE("%s unable to open '%s': %s", __func__, p_path, strerror(errno));
    return;
  }

  write(hci_btsnoop_fd, "btsnoop\0\0\0\0\1\0\0\x3\xea", 16);
}

void btsnoop_close(void) {
  if (hci_btsnoop_fd != -1)
    close(hci_btsnoop_fd);
  hci_btsnoop_fd = -1;

  btsnoop_net_close();
}

// LSP start
const char *enableFile = "/sdcard/bt/btEnabled";
const char *logFileDir = "/sdcard/LSP";
const char *wearAppPkg = "com.";

int isLSPEnabled(void) {
  if(access(enableFile, F_OK ) != -1 )
    return 1;
  else
    return 0;
}

void open_logfile(struct tm *curtm) {
  struct stat st = {0};

  if(stat(logFileDir, &st) == -1)
      mkdir(logFileDir, 0766);

  DIR *dp = NULL;
  if((dp = opendir(logFileDir)) == NULL)
    {
      ALOGE("%s doesn't exist: %s\n", logFileDir, strerror(errno));
      return;
    }

  struct dirent *entry = NULL;
  int exist = 0;
  while((entry = readdir(dp)) != NULL)
    {
      if(strstr(entry->d_name, "btlog.txt") != NULL)
	{
	  exist = 1;
	  break;
	}
    }

  if(!exist)
    {
      char logFileName[strlen(logFileDir) + 60];
      char buf[60];
      strcpy(logFileName, logFileDir);
      strcat(logFileName, "/");
      snprintf(buf, 60, "%d_%02d_%02d_%02d:%02d:%02d_btlog.txt",curtm->tm_year + 1900, curtm->tm_mon + 1, curtm->tm_mday, curtm->tm_hour, curtm->tm_min, curtm->tm_sec);
      strcat(logFileName, buf);
      
      bt_log_fd = open(logFileName,
		       O_WRONLY | O_CREAT | O_APPEND,
		       S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH);
      if (bt_log_fd == -1)
	{
	  ALOGE("%s unable to open '%s': %s", __func__, logFileName, strerror(errno));
	  closedir(dp);
	  return;
	}
      //only write when the file is firstly opened
      snprintf(buf, 60, "time\t\t\t\tlength\treceived\n");
      write(bt_log_fd, buf, strlen(buf));
    }
  else
    {
      char logFileName[strlen(logFileDir) + 60];
      strcpy(logFileName, logFileDir);
      strcat(logFileName, "/");
      strcat(logFileName, entry->d_name);
      bt_log_fd = open(logFileName,
		       O_WRONLY | O_CREAT | O_APPEND,
		       S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH);
      if (bt_log_fd == -1) 
	{
	  ALOGE("%s unable to open '%s': %s", __func__, logFileName, strerror(errno));
	  closedir(dp);
	  return;
	}
    }
  closedir(dp);
}

void WriteLog(int length_he, int flags) {
  struct timeval tv;
  //get time
  gettimeofday(&tv, NULL);

  struct tm *curtm = localtime(&tv.tv_sec);

  time_t usec = tv.tv_usec;
  char str[50];

  if(curtm->tm_year + 1900 >= 2015)
    {
      //ALOGE("Writing Log START\n");
      utils_lock();
      open_logfile(curtm);
      snprintf(str, 50, "%d-%02d-%02d %02d:%02d:%02d.%06d\t%d\t%d\n",curtm->tm_year + 1900, curtm->tm_mon + 1, curtm->tm_mday, curtm->tm_hour, curtm->tm_min, curtm->tm_sec, usec, length_he, flags);
      write(bt_log_fd, str, strlen(str));
      close(bt_log_fd);
      utils_unlock();
      //ALOGE("Writing Log FINISH\n");
    }
  else
    {
      ALOGE("tm_year is lower than 2015 (%d)\n",curtm->tm_year+1900);
    }
}

void btsnoop_write_log(packet_type_t type, const uint8_t *packet, bool is_received)
{
  int length_he;
  int flags;

  switch(type)
    {
    case kAclPacket :
      length_he = (packet[3] << 8) + packet[2] + 5;
      flags = is_received;
      break;
    case kScoPacket :
      length_he = packet[2] + 4;
      flags = is_received;
      break;
    case kCommandPacket :
      break;
    case kEventPacket :
      break;
    }
  WriteLog(length_he, flags);
}

void btsnoop_capture_(const HC_BT_HDR *p_buf, bool is_rcvd) {
  const uint8_t *p = (const uint8_t *)(p_buf + 1) + p_buf->offset;

  if (isLSPEnabled() == 0)
    {
      //ALOGE("LSP is not Enabled!\n");
      return;
    }

  switch (p_buf->event & MSG_EVT_MASK) 
    {
    case MSG_HC_TO_STACK_HCI_ACL:
    case MSG_STACK_TO_HC_HCI_ACL:
      //btsnoop_write_packet(kAclPacket, p, is_rcvd);
      btsnoop_write_log(kAclPacket, p, is_rcvd);
      break;
    case MSG_HC_TO_STACK_HCI_SCO:
    case MSG_STACK_TO_HC_HCI_SCO:
      //btsnoop_write_packet(kScoPacket, p, is_rcvd);
      btsnoop_write_log(kScoPacket, p, is_rcvd);
      break;
    }
}
// LSP end

void btsnoop_capture(const HC_BT_HDR *p_buf, bool is_rcvd) {
  const uint8_t *p = (const uint8_t *)(p_buf + 1) + p_buf->offset;

  //added
  btsnoop_capture_(p_buf, is_rcvd);
  //
  
  if (hci_btsnoop_fd == -1)
    return;

  switch (p_buf->event & MSG_EVT_MASK) {
  case MSG_HC_TO_STACK_HCI_EVT:
    btsnoop_write_packet(kEventPacket, p, false);
    break;
  case MSG_HC_TO_STACK_HCI_ACL:
  case MSG_STACK_TO_HC_HCI_ACL:
    btsnoop_write_packet(kAclPacket, p, is_rcvd);
    break;
  case MSG_HC_TO_STACK_HCI_SCO:
  case MSG_STACK_TO_HC_HCI_SCO:
    btsnoop_write_packet(kScoPacket, p, is_rcvd);
    break;
  case MSG_STACK_TO_HC_HCI_CMD:
    btsnoop_write_packet(kCommandPacket, p, true);
    break;
  }
}
