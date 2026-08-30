package com.ai.assistance.quro.file.provider.remote;

import com.ai.assistance.quro.file.provider.remote.IRemoteFileSystem;
import com.ai.assistance.quro.file.provider.remote.IRemoteFileSystemProvider;
import com.ai.assistance.quro.file.provider.remote.IRemotePosixFileAttributeView;
import com.ai.assistance.quro.file.provider.remote.IRemotePosixFileStore;
import com.ai.assistance.quro.file.provider.remote.ParcelableObject;

interface IRemoteFileService {
    IRemoteFileSystemProvider getRemoteFileSystemProviderInterface(String scheme);

    IRemoteFileSystem getRemoteFileSystemInterface(in ParcelableObject fileSystem);

    IRemotePosixFileStore getRemotePosixFileStoreInterface(in ParcelableObject fileStore);

    IRemotePosixFileAttributeView getRemotePosixFileAttributeViewInterface(
        in ParcelableObject attributeView
    );
}
