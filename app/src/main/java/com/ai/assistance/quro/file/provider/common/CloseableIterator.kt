/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.common

import java.io.Closeable

interface CloseableIterator<T> : Iterator<T>, Closeable
