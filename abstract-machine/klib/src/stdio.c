#include <am.h>
#include <klib.h>
#include <klib-macros.h>
#include <stdarg.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)


static int print_unsigned(char *out, unsigned int val) {
  char buffer[32]; 
  int i = 0;
  
  if (val == 0) {
    *out = '0';
    return 1;
  }

  while (val > 0) {
    buffer[i++] = (val % 10) + '0';
    val /= 10;
  }

  int len = i;
  while (i > 0) {
    *out++ = buffer[--i];
  }
  return len;
}

static int print_int(char *out, int val) {
  int len = 0;
  
  if (val < 0) {
    *out++ = '-';
    len++;
    unsigned int uval = (unsigned int)(-(val + 1)) + 1;
    len += print_unsigned(out, uval);
  } else {
    len += print_unsigned(out, (unsigned int)val);
  }
  return len;
}
// 1. 核心逻辑放在 vsprintf 中，注意参数是 va_list ap
int vsprintf(char *out, const char *fmt, va_list ap) {
  char *p = out; 
  
  while (*fmt) {
    if (*fmt != '%') {
      *p++ = *fmt++;
      continue;
    }

    fmt++; 

    switch (*fmt) {
      case 's': {
        const char *s = va_arg(ap, const char *); // 使用 ap
        while (*s) *p++ = *s++;
        break;
      }
      case 'd': {
        int d = va_arg(ap, int); // 使用 ap
        p += print_int(p, d);
        break;
      }
      case 'u': {
        int d = va_arg(ap, int); // 使用 ap
        p += print_unsigned(p, d);
        break;
      }
      case '%':
        *p++ = '%';
        break;
      default:
        *p++ = '%';
        if (*fmt) *p++ = *fmt; 
        break;
    }
    if (*fmt) fmt++; 
  }

  *p = '\0'; 
  return p - out; 
}

// 2. sprintf 变成了一个简单的“包装器”
int sprintf(char *out, const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  int len = vsprintf(out, fmt, ap); // 调用 v 版本
  va_end(ap);
  return len;
}

// 3. printf 也是包装器
int printf(const char *fmt, ...) {
  char out[1024];
  va_list ap;
  va_start(ap, fmt);
  int len = vsprintf(out, fmt, ap); 
  va_end(ap);
  
  for(int i = 0; i < len; i++) {
    putch(out[i]); 
  }

  return len; 
}

int snprintf(char *out, size_t n, const char *fmt, ...) {
  panic("Not implemented");
}

int vsnprintf(char *out, size_t n, const char *fmt, va_list ap) {
  panic("Not implemented");
}

#endif
