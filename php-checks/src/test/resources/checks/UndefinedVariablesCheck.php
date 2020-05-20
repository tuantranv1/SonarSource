<?php

function foo() {
  global $a;
  if (bla) {
    $a = "abc";
    echo $a;
  } else {
    echo $a; // Noncompliant {{Used and not defined}}
//       ^^
  }
}
