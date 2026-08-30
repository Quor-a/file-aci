/*
 * Copyright (c) 2022 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.common

import java8.nio.file.Path
import java8.nio.file.WatchEvent

class LocalWatchKey(
    watchService: LocalWatchService,
    path: Path,
    @Volatile
    internal var kinds: Set<WatchEvent.Kind<*>>
) : AbstractWatchKey<LocalWatchKey, Path>(watchService, path)
