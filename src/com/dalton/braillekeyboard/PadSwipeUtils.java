/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class PadSwipeUtils {
    private static final Map<Integer, Pad.Swipe> SWIPE_MAP = buildSwipeMap();

    private PadSwipeUtils() {
    }

    static Pad.Swipe fromValue(int value) {
        Pad.Swipe swipe = SWIPE_MAP.get(Integer.valueOf(value));
        return swipe == null ? Pad.Swipe.UNKNOWN : swipe;
    }

    private static Map<Integer, Pad.Swipe> buildSwipeMap() {
        Map<Integer, Pad.Swipe> swipes = new HashMap<Integer, Pad.Swipe>();
        put(swipes, Pad.Swipe.NONE, 0);
        put(swipes, Pad.Swipe.ONE_LEFT, 1);
        put(swipes, Pad.Swipe.ONE_RIGHT, 2);
        put(swipes, Pad.Swipe.ONE_DOWN, 3);
        put(swipes, Pad.Swipe.ONE_UP, 4);
        put(swipes, Pad.Swipe.TWO_LEFT, 8);
        put(swipes, Pad.Swipe.TWO_RIGHT, 16);
        put(swipes, Pad.Swipe.TWO_DOWN, 24);
        put(swipes, Pad.Swipe.TWO_UP, 32);
        put(swipes, Pad.Swipe.THREE_LEFT, 64);
        put(swipes, Pad.Swipe.THREE_RIGHT, 128);
        put(swipes, Pad.Swipe.THREE_DOWN, 192);
        put(swipes, Pad.Swipe.THREE_UP, 256);
        put(swipes, Pad.Swipe.FOUR_LEFT, 512);
        put(swipes, Pad.Swipe.FOUR_RIGHT, 1024);
        put(swipes, Pad.Swipe.FOUR_DOWN, 1536);
        put(swipes, Pad.Swipe.FOUR_UP, 2048);
        put(swipes, Pad.Swipe.FIVE_LEFT, 4096);
        put(swipes, Pad.Swipe.FIVE_RIGHT, 8192);
        put(swipes, Pad.Swipe.FIVE_DOWN, 12288);
        put(swipes, Pad.Swipe.FIVE_UP, 16384);
        put(swipes, Pad.Swipe.SIX_LEFT, 32768);
        put(swipes, Pad.Swipe.SIX_RIGHT, 65536);
        put(swipes, Pad.Swipe.SIX_DOWN, 98304);
        put(swipes, Pad.Swipe.SIX_UP, 131072);

        put(swipes, Pad.Swipe.HOLD_SIX_LEFT, 229377, 229384, 229440);
        put(swipes, Pad.Swipe.HOLD_SIX_RIGHT, 229378, 229392, 229504);
        put(swipes, Pad.Swipe.HOLD_SIX_DOWN, 229379, 229400, 229568);
        put(swipes, Pad.Swipe.HOLD_SIX_UP, 229380, 229408, 229632);

        put(swipes, Pad.Swipe.HOLD_THREE_LEFT, 960, 4544, 33216);
        put(swipes, Pad.Swipe.HOLD_THREE_RIGHT, 1472, 8640, 65984);
        put(swipes, Pad.Swipe.HOLD_THREE_DOWN, 1984, 12736, 98752);
        put(swipes, Pad.Swipe.HOLD_THREE_UP, 2496, 16832, 131520);

        put(swipes, Pad.Swipe.HOLD_ONE_UP, 2055, 16391, 131078);
        put(swipes, Pad.Swipe.HOLD_ONE_DOWN, 1543, 12295, 98311);
        put(swipes, Pad.Swipe.HOLD_ONE_RIGHT, 1031, 8199, 65543);
        put(swipes, Pad.Swipe.HOLD_ONE_LEFT, 519, 4103, 32775);

        put(swipes, Pad.Swipe.HOLD_FOUR_LEFT, 3585, 3592, 3648);
        put(swipes, Pad.Swipe.HOLD_FOUR_RIGHT, 3586, 3600, 3712);
        put(swipes, Pad.Swipe.HOLD_FOUR_DOWN, 3587, 3608, 3776);
        put(swipes, Pad.Swipe.HOLD_FOUR_UP, 3588, 3616, 3840);
        return Collections.unmodifiableMap(swipes);
    }

    private static void put(Map<Integer, Pad.Swipe> swipes, Pad.Swipe swipe,
            int... values) {
        for (int value : values) {
            swipes.put(Integer.valueOf(value), swipe);
        }
    }
}
