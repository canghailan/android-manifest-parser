一个纯Java、无任何第三方依赖的 AndroidManifest.xml SAX解析工具。

实现解析相关代码不超过300行。
AndroidManifestReader、ApkInputSource分别实现了org.xml.sax.XMLReader及org.xml.sax.InputSource接口，
可以充分利用Java标准库中的代码进行更多的处理。
如转为Document：
```
transformer.transform(
new SAXSource(new AndroidManifestReader(), new ApkInputSource(new URL("http://xxx.apk"))),
new DOMResult(document));
```
或者输出为XML：
```
transformer.transform(
new SAXSource(new AndroidManifestReader(), new ApkInputSource(new URL("http://xxx.apk"))),
new StreamResult(writer));
```