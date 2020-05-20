<?php

if (foo) {
  echo $x;
}
echo $y;

if (foo) {
  echo $x;
} else {
  echo $y;
}



function foo() {
  global $a;
  if (bla) {
    $a = "abc";
    echo $a;
  } else {
    echo $a; // Compliant {{Used and not defined}}
//       ^^
  }
}
