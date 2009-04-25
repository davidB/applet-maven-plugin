<?php
// https://jdk6.dev.java.net/plugin2/jnlp/#CODEBASE

$url = "file://" . str_replace("\\", "/", __FILE__);
if (isset($_SERVER['SERVER_ADDR'])) {
  $url = "http://" . $_SERVER['SERVER_ADDR'] . ":" . $_SERVER['SERVER_PORT'] . $_SERVER['REQUEST_URI'];
  header('Content-type: application/x-java-jnlp-file');
}
$codebase = "";
$href = $url;
$pos = strrpos($url, "/");
if ($pos === false) { // note: three equal signs
  // not found...
} else {
  $codebase = substr($url, 0, $pos+1);
  $href = substr($url, $pos +1);
}
echo " -- url : " . $url;
echo " -- codebase : " . $codebase;
echo " -- href : " . $href;
echo " -- " . $_SERVER['SERVER_NAME'];
echo " -- " . $_SERVER['SERVER_PORT'];
echo " -- " . $_SERVER['REQUEST_URI'];
echo " 
-- " . __FILE__;
?>