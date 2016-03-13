# AndroidManifest解析器

一个纯Java、实现了SAX接口、无任何第三方依赖的 AndroidManifest.xml 解析工具。

## AndroidManifest.xml文件格式
![AndroidManifest.xml](http://static.whohow.cc/images/android-manifest-parser/android-manifest.png)

```
Binary AndroidManifest.xml
 Magic Number(0x00080003)   4bytes // 魔数
 File Size                  4bytes // 文件长度
 String Chunk // 字符串常量池
   Chunk Type(0x001C0001)   4bytes // Chunk类型
   Chunk Size               4bytes // Chunk长度
   String Count             4bytes // 字符串个数
   Style Count              4bytes
   Unknown                  4bytes
   String Pool Offset       4bytes // 字符串常量池相对于Chunk偏移值
   Style Pool Offset        4bytes
   String Offsets           4bytes * StringCount // 字符串相对于StringPoolOffset偏移值
   Style Offsets            4bytes * StyleCount
   String Pool *
     String Length          2bytes // 字符串长度
     String Content         2bytes * StringLength // 字符串内容
     \0                     2bytes // \0
   Style Pool
 ResourceId Chunk
   Chunk Type(0x00080180)   4bytes
   Chunk Size               4bytes
   ResourceIds              4bytes * (ChunkSize / 4 - 2)
 XmlContent Chunk *
   Start Namespace Chunk
     Chunk Type(0x00100100) 4bytes // Chunk类型
     Chunk Size             4bytes // Chunk长度
     Line Number            4bytes
     Unknown                4bytes
     Prefix                 4bytes // namespace对应的前缀（常量池地址）
     Uri                    4bytes // namespace对应的URI（常量池地址）
   End Namespace Chunk
     Chunk Type(0x00100101) 4bytes
     Chunk Size             4bytes
     Line Number            4bytes
     Unknown                4bytes
     Prefix                 4bytes
     Uri                    4bytes
   Start Tag Chunk // 开始标签
     Chunk Type(0x00100102) 4bytes // Chunk类型
     Chunk Size             4bytes // Chunk长度
     Line Number            4bytes
     Unknown                4bytes
     Namespace Uri          4bytes // 标签Namespace URI
     Name                   4bytes // 标签名
     Flags                  4bytes
     Attribute Count        4bytes // 属性个数
     Class Attribute        4bytes
     Attributes *
       Namespace Uri        4bytes // 属性Namespace URI
       Name                 4bytes // 属性名
       Value                4bytes // 字符串属性值（常量池地址）
       Type                 4bytes // 属性值类型
       Data                 4bytes // 属性值
   End Tag Chunk // 结束标签
     Chunk Type(0x00100103) 4bytes // Chunk类型
     Chunk Size             4bytes // Chunk长度
     Line Number            4bytes
     Unknown                4bytes
     Namespace Uri          4bytes
     Name                   4bytes
   Text Chunk // 文本内容
     Chunk Type(0x00100104) 4bytes // Chunk类型
     Chunk Size             4bytes // Chunk长度
     Line Number            4bytes
     Unknown                4bytes
     Name                   4bytes // 文本（常量池地址）
     Unknown                4bytes
     Unknown                4bytes
```

## 实现
### 1. 使用```java.nio.ByteBuffer```操作字节序及字节。

```
ByteBuffer.order
ByteBuffer.get
ByteBuffer.getInt
ByteBuffer.getFloat
```
### 2. 按照文件格式自顶向下解析。

```
parse
  parseStringChunk
    parseString
  parseResourceIdChunk
  parseStartNamespaceChunk
  parseEndNamespaceChunk
  parseStartTagChunk
    parseAttribute
      parseAttributeValue
  parseEndTagChunk
  parseTextChunk
```
### 3. 实现SAX相关接口以便重用代码。 
 
```
public class AndroidManifestReader implements XMLReader {
}
public class ApkInputSource extends InputSource {
}

// 转为Document
transformer.transform(
new SAXSource(new AndroidManifestReader(), new ApkInputSource(new URL("http://xxx.apk"))),
new DOMResult(document));

// 输出XML
transformer.transform(
new SAXSource(new AndroidManifestReader(), new ApkInputSource(new URL("http://xxx.apk"))),
new StreamResult(writer));
```

## Licence
[The MIT License (MIT)](https://opensource.org/licenses/MIT)

## 参考文档
如有侵权，请告知，立即删除相关文字及图片。

1. AndroidManifest.xml文件格式  
   http://blog.csdn.net/jiangwei0910410003/article/details/50568487
2. 属性值解析相关代码  
   https://github.com/xgouchet/AXML
