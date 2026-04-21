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

import com.dalton.braillekeyboard.Pad.Coords;

final class BrailleViewTouchUtils {
    private BrailleViewTouchUtils() {
    }

    static int countDotsDown(Coords[] dots) {
        int count = 0;
        for (Coords coords : dots) {
            if (coords != null) {
                ++count;
            }
        }
        return count;
    }

    static boolean updatePointer(Coords[] coords, int id, int x, int y,
            boolean reset) {
        for (int i = 0; i < coords.length; i++) {
            if (coords[i] != null && coords[i].id == id) {
                if (reset) {
                    coords[i] = new Coords(id, x, y);
                }
                coords[i].setSecondCords(x, y);
                return true;
            }
        }
        return false;
    }
}
