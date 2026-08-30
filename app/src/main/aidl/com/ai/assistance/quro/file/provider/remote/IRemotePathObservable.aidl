package com.ai.assistance.quro.file.provider.remote;

import com.ai.assistance.quro.file.provider.remote.ParcelableException;
import com.ai.assistance.quro.file.util.RemoteCallback;

interface IRemotePathObservable {
    void addObserver(in RemoteCallback observer);

    void close(out ParcelableException exception);
}
