/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include <isa.h>

/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>
#include <stdio.h>
#include <string.h>
enum {
  TK_NOTYPE = 256, TK_EQ,
  TK_NUM_DEC, TK_NUM_HEX,
  TK_L,TK_R,TK_NEG,
  /* TODO: Add more token types */

};

static struct rule {
  const char *regex;
  int token_type;
} rules[] = {

  /* TODO: Add more rules.
   * Pay attention to the precedence level of different rules.
   */

  {" +", TK_NOTYPE},    // spaces
  {"\\+", '+'},         // plus
  {"\\-", '-'}, 
  {"\\*", '*'}, 
  {"/", '/'}, 
  {"[0-9]+",TK_NUM_DEC},
  {"0x[0-9a-fA-F]+",TK_NUM_HEX},
  {"\\(",TK_L},
  {"\\)",TK_R},
  {"==", TK_EQ},        // equal
};

#define NR_REGEX ARRLEN(rules)

static regex_t re[NR_REGEX] = {};

/* Rules are used for many times.
 * Therefore we compile them only once before any usage.
 */
void init_regex() {
  int i;
  char error_msg[128];
  int ret;

  for (i = 0; i < NR_REGEX; i ++) {
    ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
    if (ret != 0) {
      regerror(ret, &re[i], error_msg, 128);
      panic("regex compilation failed: %s\n%s", error_msg, rules[i].regex);
    }
  }
}

typedef struct token {
  int type;
  char str[32];
} Token;

static Token tokens[32] __attribute__((used)) = {};
static int nr_token __attribute__((used))  = 0;
static void get_substr(char * str,int len,char* src){
  for(int i = 0;i<len;i++){
    src[i] = str[i];
  }
  src[len] = '\0';
}
static bool make_token(char *e) {
  int position = 0;
  int i;
  regmatch_t pmatch;

  nr_token = 0;

  while (e[position] != '\0') {
    /* Try all rules one by one. */
    for (i = 0; i < NR_REGEX; i ++) {
      if (regexec(&re[i], e + position, 1, &pmatch, 0) == 0 && pmatch.rm_so == 0) {
        char *substr_start = e + position;
        int substr_len = pmatch.rm_eo;

        Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s",
            i, rules[i].regex, position, substr_len, substr_len, substr_start);

        if(rules[i].token_type != TK_NOTYPE){
          get_substr(substr_start,substr_len,tokens[nr_token].str);
          tokens[nr_token].type = rules[i].token_type;
          nr_token++;
        }
        position += substr_len;
        break;
      }
    }

    if (i == NR_REGEX) {
      printf("no match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }

  return true;
}

static bool check_parentheses(int p,int q){
  int cnt = 0;
  if(tokens[p].type != TK_L || tokens[q].type != TK_R) return false; 
  while(p <= q){
    if(tokens[p].type == TK_L){
      cnt++;
    }else if(tokens[p].type == TK_R){
      cnt--;
    }
    if(cnt < 0) return false;
    if(p < q && cnt == 0) return false;
    p++;
  }
  if ( cnt == 0) return true;
  else return false;
}
#include <stdlib.h>
static int get_main_op(int p,int q){
  int idx = p;
  int res = p;
  int cnt = 0;
  while(idx <= q){
    if(tokens[idx].type == '+' || tokens[idx].type == '-' || 
      tokens[idx].type == '*' || tokens[idx].type == '/' ){
        if( cnt == 0 ){
          if(tokens[idx].type == '+' || tokens[idx].type == '-') res = idx;
          else{
            if(tokens[res].type != '+' && tokens[res].type != '-') res = idx;
          }
        }
    }else if(tokens[idx].type == TK_L) cnt++;
    else if(tokens[idx].type == TK_R) cnt--;
    idx++;
  }
  return res;
}
static void get_expr_val(int p,int q,word_t* val){
  if(p > q)
    return;
  else if (p == q) {
    if(tokens[p].type == TK_NUM_DEC || tokens[p].type == TK_NUM_HEX){
      int num;
      if(tokens[p].type == TK_NUM_DEC)
        num = atoi(tokens[p].str);
      else sscanf(tokens[p].str,"%x",&num);
      *val = num;
      return;
    }else return;
  }
  else if (check_parentheses(p, q) == true) {
    get_expr_val(p + 1, q - 1,val);
    return;
  }
  else {
    int main_op = get_main_op(p,q);
    printf("p %d q %d main_op %d\n",p,q,main_op);
    word_t val1,val2;
    get_expr_val(p,main_op-1, &val1);
    get_expr_val(main_op+1,q, &val2);
    switch (tokens[main_op].type)
    {
      case '+': *val = val1 + val2; break;
      case '-': *val = val1 - val2; break;
      case '*': *val = val1 * val2; break;
      case '/': *val = val1 / val2; break;
      default: assert(0);
    }
    return;
  }
}

word_t expr(char *e, bool *success) {
  if (!make_token(e)) {
    *success = false;
    return 0;
  }
  /* TODO: Insert codes to evaluate the expression. */
  word_t res;
  get_expr_val(0,nr_token-1,&res);
  return res;
}
