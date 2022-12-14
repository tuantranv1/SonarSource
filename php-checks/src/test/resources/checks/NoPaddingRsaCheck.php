<?php

namespace ns1 {
  function foo($data, $crypted, $key) {
    openssl_public_encrypt($data, $crypted, $key, \OPENSSL_PKCS1_OAEP_PADDING);
  }
}

function f() {
  openssl_public_encrypt($data, $crypted, $key, OPENSSL_NO_PADDING);    // Noncompliant {{Use an RSA algorithm with a OAEP padding: OPENSSL_PKCS1_OAEP_PADDING.}}
//                                              ^^^^^^^^^^^^^^^^^^

  openssl_public_encrypt($data, $crypted, $key, OPENSSL_PKCS1_PADDING); // Noncompliant
  openssl_public_encrypt($data, $crypted, $key, OPENSSL_SSLV23_PADDING); // Noncompliant
  openssl_public_encrypt($data, $crypted, $key, OPENSSL_PKCS1_OAEP_PADDING);
  openssl_public_encrypt($data, $crypted, padding:OPENSSL_PKCS1_OAEP_PADDING, key:$key);
  openssl_public_encrypt($data, $crypted, padding:OPENSSL_NO_PADDING, key:$key); // Noncompliant

  // by default OPENSSL_PKCS1_PADDING is used
  openssl_public_encrypt($data, $crypted, $key); // Noncompliant
//^^^^^^^^^^^^^^^^^^^^^^


  foo($data, $crypted, $key, OPENSSL_NO_PADDING);
  openssl_public_encrypt($data, $crypted, $key, $padding);
}
