/*
 * Copyright 2020 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.common.encoding;

import com.linecorp.armeria.common.ContentTooLargeException;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.annotation.UnstableApi;

/**
 * An interface for objects that apply HTTP content decoding to incoming {@link HttpData}.
 * Implement this interface to use content decoding schemes not built-in to the JDK.
 */
public interface StreamDecoder extends com.linecorp.armeria.client.encoding.StreamDecoder {

    /**
     * Decodes an {@link HttpData} and returns the decoded {@link HttpData}.
     *
     * @throws ContentTooLargeException if the total length of the decoded {@link HttpData} exceeds
     *                                  {@link #maxLength()}.
     */
    @Override
    HttpData decode(HttpData obj);

    /**
     * Closes the decoder and returns any decoded data that may be left over.
     *
     * @throws ContentTooLargeException if the total length of the decoded {@link HttpData} exceeds
     *                                  {@link #maxLength()}.
     */
    @Override
    HttpData finish();

    /**
     * Returns the maximum allowed length of the content decoded.
     * {@code 0} means unlimited.
     */
    @Override
    @UnstableApi
    int maxLength();
}
