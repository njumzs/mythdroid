/*
    MythDroid: Android MythTV Remote
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mythdroid;

/** Program categories and their colours */
public enum Category {
    unknown         (0xff404040),   shopping        (0xff301040),
    educational     (0xff202080),   musical         (0xffa00040),
    news            (0xff00aa00),   reality         (0xff500020),
    cooking         (0xff003080),   documentary     (0xff5000a0),
    doc             (0xff5000a0),   sport           (0xff204040),
    sports          (0xff204040),   sportsevent     (0xff204040),
    sportstalk      (0xff202040),   music           (0xff6020a0),
    musicandarts    (0xff6020a0),   movies          (0xff108000),
    movie           (0xff108000),   film            (0xff108000),
    drama           (0xff0020c0),   crime           (0xff300080),
    animals         (0xff00c040),   nature          (0xff00c040),
    sciencenature   (0xff00c040),   comedy          (0xff808000),
    comedydrama     (0xff808010),   romancecomedy   (0xff808020),
    sitcom          (0xff804000),   scifi           (0xff106060),
    sciencefiction  (0xff106060),   scififantasy    (0xff106060),
    fantasy         (0xff106060),   horror          (0xffc03030),
    suspense        (0xffc03030),   action          (0xffa00060),
    actionadv       (0xffa04060),   adventure       (0xffa04000),
    romance         (0xff800020),   health          (0xff2000a0),
    homehowto       (0xff804000),   homeimprovement (0xff804000),
    howto           (0xff804000),   housegarden     (0xff804000),
    foodtravel      (0xff808000),   children        (0xff108040),
    kids            (0xff108040),   animated        (0xff108060),
    gameshow        (0xff703000),   interests       (0xff703030),
    talkshow        (0xff007070),   biography       (0xff500080),
    fashion         (0xff0060a0),   docudrama       (0xff8000c0),
    selfimprovement (0xff800040),   exercise        (0xff002080),
    auto            (0xffa03000),   soap            (0xff408020),
    soaps           (0xff408020);

    private int color;

    private Category(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }
}
