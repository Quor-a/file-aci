package com.ai.assistance.quro.file.provider.remote;

import com.ai.assistance.quro.file.provider.common.ParcelableFileTime;
import com.ai.assistance.quro.file.provider.common.ParcelablePosixFileMode;
import com.ai.assistance.quro.file.provider.common.PosixGroup;
import com.ai.assistance.quro.file.provider.common.PosixUser;
import com.ai.assistance.quro.file.provider.remote.ParcelableException;
import com.ai.assistance.quro.file.provider.remote.ParcelableObject;

interface IRemotePosixFileAttributeView {
    ParcelableObject readAttributes(out ParcelableException exception);

    void setTimes(
        in ParcelableFileTime lastModifiedTime,
        in ParcelableFileTime lastAccessTime,
        in ParcelableFileTime createTime,
        out ParcelableException exception
    );

    void setOwner(in PosixUser owner, out ParcelableException exception);

    void setGroup(in PosixGroup group, out ParcelableException exception);

    void setMode(in ParcelablePosixFileMode mode, out ParcelableException exception);

    void setSeLinuxContext(in ParcelableObject context, out ParcelableException exception);

    void restoreSeLinuxContext(out ParcelableException exception);
}
