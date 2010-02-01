try {

def jarlist = new File(basedir, 'target/jws/launch.html')
assert jarlist.exists()
jarlist.eachLine { line -> assert line.indexOf('${') < 0 : "non evaluated value in :" + line }

def lib0 = new File(basedir, 'target/jws/test-proguard-testing-all.jar')
assert lib0.exists()

def lib1 = new File(basedir, 'target/jws/commons-io-1.3.2.jar.pack.lzma')
assert !lib1.exists()

return true

} catch(Throwable e) {
  e.printStackTrace()
  return false
}