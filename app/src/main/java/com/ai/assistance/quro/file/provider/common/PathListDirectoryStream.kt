/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.common

import java8.nio.file.DirectoryStream
import java8.nio.file.Path

class PathListDirectoryStream(
    paths: List<Path>,
    filter: DirectoryStream.Filter<in Path>
) : PathIteratorDirectoryStream(paths.iterator(), null, filter)
