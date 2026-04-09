#include <am.h>
#include <klib.h>
#include <klib-macros.h>
#include <stdarg.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

int printf(const char *fmt, ...) {
    char out[1024];
    va_list ap;
    va_start(ap, fmt);
    vsprintf(out, fmt, ap);

    va_end(ap);

    for(int i = 0;i<1024;i++){
      if(out[i] != 0)
        putch(out[i]);
    }

    return strlen(out);
}

int vsprintf(char *out, const char *fmt, va_list ap) {
  panic("Not implemented");
}
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

int sprintf(char *out, const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);

  char *p = out; 
  
  while (*fmt) {
    if (*fmt != '%') {
      *p++ = *fmt++;
      continue;
    }

    fmt++; 

    switch (*fmt) {
      case 's': {
        const char *s = va_arg(ap, const char *);
        while (*s) {
          *p++ = *s++;
        }
        break;
      }
      case 'd': {
        int d = va_arg(ap, int);
        p += print_int(p, d);
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
  va_end(ap);
  
  return p - out; 
}


int snprintf(char *out, size_t n, const char *fmt, ...) {
  panic("Not implemented");
}

int vsnprintf(char *out, size_t n, const char *fmt, va_list ap) {
  panic("Not implemented");
}

#endif
