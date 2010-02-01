try {

def jarlist = new File(basedir, 'target/jws/jarlist.txt')
assert jarlist.exists()
jarlist.eachLine { line -> assert line.indexOf('${') < 0 : "non evaluated value in :" + line }

def lib0 = new File(basedir, 'target/jws/commons-io-1.3.2.jar')
assert lib0.exists()

def lib1 = new File(basedir, 'target/jws/commons-io-1.3.2.jar.pack.lzma')
def lib1tmp = new File(basedir, 'target/jws/commons-io-1.3.2-tmp3.jar.pack.lzma')
assert lib1.exists()
assert !lib1tmp.exists()

return true

} catch(Throwable e) {
  e.printStackTrace()
  return false
}