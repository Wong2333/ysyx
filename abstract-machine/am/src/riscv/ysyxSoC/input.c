#include <am.h>
#include <npc.h>
#include <riscv/riscv.h>

static const int normal_key_map[256] = {
    // 0x00
    AM_KEY_NONE,
    // 0x01 - 0x0F: ESC, F1...F12, BACKSPACE (部分)
    [0x01] AM_KEY_ESCAPE, [0x3B] AM_KEY_F1, [0x3C] AM_KEY_F2, [0x3D] AM_KEY_F3,
    [0x3E] AM_KEY_F4, [0x3F] AM_KEY_F5, [0x40] AM_KEY_F6, [0x41] AM_KEY_F7,
    [0x42] AM_KEY_F8, [0x43] AM_KEY_F9, [0x44] AM_KEY_F10, [0x57] AM_KEY_F11, [0x58] AM_KEY_F12,
    
    // 数字键 1-0 (扫描码 0x02 - 0x0B) -> 宏定义顺序 1,2...9,0
    [0x02] AM_KEY_1, [0x03] AM_KEY_2, [0x04] AM_KEY_3, [0x05] AM_KEY_4, [0x06] AM_KEY_5,
    [0x07] AM_KEY_6, [0x08] AM_KEY_7, [0x09] AM_KEY_8, [0x0A] AM_KEY_9, [0x0B] AM_KEY_0,
    
    // 符号键
    [0x0C] AM_KEY_MINUS, [0x0D] AM_KEY_EQUALS, [0x0E] AM_KEY_BACKSPACE,
    [0x0F] AM_KEY_TAB,
    
    // 0x10 - 0x1F: Q...P, [
    [0x10] AM_KEY_Q, [0x11] AM_KEY_W, [0x12] AM_KEY_E, [0x13] AM_KEY_R, [0x14] AM_KEY_T,
    [0x15] AM_KEY_Y, [0x16] AM_KEY_U, [0x17] AM_KEY_I, [0x18] AM_KEY_O, [0x19] AM_KEY_P,
    [0x1A] AM_KEY_LEFTBRACKET, [0x1B] AM_KEY_RIGHTBRACKET,
    [0x1D] AM_KEY_LCTRL, // 左 Ctrl
    [0x1E] AM_KEY_A, [0x1F] AM_KEY_S,
    
    // 0x20 - 0x2F: D...L, ; '
    [0x20] AM_KEY_D, [0x21] AM_KEY_F, [0x22] AM_KEY_G, [0x23] AM_KEY_H, [0x24] AM_KEY_J,
    [0x25] AM_KEY_K, [0x26] AM_KEY_L,
    [0x27] AM_KEY_SEMICOLON, [0x28] AM_KEY_APOSTROPHE, [0x29] AM_KEY_GRAVE,
    [0x2A] AM_KEY_LSHIFT, // 左 Shift
    [0x2B] AM_KEY_BACKSLASH,
    
    // 0x2C - 0x3A: Z...M, , . / Space Caps
    [0x2C] AM_KEY_Z, [0x2D] AM_KEY_X, [0x2E] AM_KEY_C, [0x2F] AM_KEY_V, [0x30] AM_KEY_B,
    [0x31] AM_KEY_N, [0x32] AM_KEY_M,
    [0x33] AM_KEY_COMMA, [0x34] AM_KEY_PERIOD, [0x35] AM_KEY_SLASH,
    [0x36] AM_KEY_RSHIFT, // 右 Shift
    [0x38] AM_KEY_LALT,   // 左 Alt
    [0x39] AM_KEY_SPACE,
    [0x3A] AM_KEY_CAPSLOCK,
};

// 扩展按键映射表 (扫描码以 0xE0 开头的键)
// 这些键的扫描码通常是 E0 XX，我们用 XX 作为索引查此表
static const int ext_key_map[256] = {
    [0x48] AM_KEY_UP,       // E0 48
    [0x50] AM_KEY_DOWN,     // E0 50
    [0x4B] AM_KEY_LEFT,     // E0 4B
    [0x4D] AM_KEY_RIGHT,    // E0 4D
    [0x52] AM_KEY_INSERT,   // E0 52
    [0x53] AM_KEY_DELETE,   // E0 53
    [0x47] AM_KEY_HOME,     // E0 47
    [0x4F] AM_KEY_END,      // E0 4F
    [0x49] AM_KEY_PAGEUP,   // E0 49
    [0x51] AM_KEY_PAGEDOWN, // E0 51
    
    [0x1D] AM_KEY_RCTRL,    // E0 1D (右 Ctrl)
    [0x38] AM_KEY_RALT,     // E0 38 (右 Alt)
    [0x5D] AM_KEY_APPLICATION, // E0 5D (Application 键)
};

int scancode_to_am_key(uint8_t scancode, int is_extended) {
    if (is_extended) {
        if (scancode < 256) {
            return ext_key_map[scancode];
        }
    } else {
        if (scancode < 256) {
            return normal_key_map[scancode];
        }
    }
    return AM_KEY_NONE;
}

void __am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {
  uint8_t code = inb(KEYBOARD);
  if(code == 0xf0){
    while(!(code = inb(KEYBOARD)));
    kbd->keydown = 1;
    kbd->keycode = scancode_to_am_key(code, 0);
  }else{
    kbd->keydown = 0;
    kbd->keycode = scancode_to_am_key(code, 0);
  }
}
