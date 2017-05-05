package zz.aimsicd.lite.enums;

import android.support.annotation.ColorRes;
import android.support.annotation.StringRes;

import zz.aimsicd.lite.R;

/*

See: colors.xml:

    <color name="white">#ffffffff</color>
    <color name="transparent_white">#50ffffff</color>
    <color name="black">#ff000000</color>
    <color name="action_bar_color">#ff0d0d0d</color>
    <color name="green_text">#ff00FF00</color>
    <color name="medium_blue">#ff33B5E5</color>
    <color name="standard_yellow">#ffea59</color>
    <color name="standard_grey">#dddddd</color>
    <color name="red_text">#a00000</color>                 <!-- deep red -->
    <color name="bright_red_text">#ffff0000</color>        <!-- bright red -->

 */

public enum Status {
    IDLE(R.string.status_idle, R.color.material_grey_400),   // Grey      0x7f0b008e
    OK(R.string.status_ok, R.color.material_light_green_A700),             // Green     0x7f0b00bf
    MEDIUM(R.string.status_medium, R.color.material_yellow_A700),          // Yellow    0x7f0b0122
    HIGH(R.string.status_high, R.color.material_orange_A700),              // Orange    0x7f0b00db
    DANGER(R.string.status_danger, R.color.material_red_A700),             // Red       0x7f0b0105
    SKULL(R.string.status_skull, R.color.material_black);                   // Black     0x7f0b002d

    @StringRes
    private int name;

    @ColorRes
    private int color;

    Status(@StringRes int name, @ColorRes int color) {
        this.name = name;
        this.color = color;
    }

    @StringRes
    public int getName() {
        return name;
    }

    @ColorRes
    public int getColor() {
        return color;
    }
}
