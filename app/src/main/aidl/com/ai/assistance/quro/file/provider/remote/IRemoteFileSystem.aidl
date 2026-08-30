package com.ai.assistance.quro.file.provider.remote;

import com.ai.assistance.quro.file.provider.remote.ParcelableException;

interface IRemoteFileSystem {
    void close(out ParcelableException exception);
}
