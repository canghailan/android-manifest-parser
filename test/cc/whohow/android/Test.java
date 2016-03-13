package cc.whohow.android;

import java.io.StringWriter;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

public class Test {
	public static void main(String[] args) throws Exception {
		String apk = "http://dldir1.qq.com/qqfile/QQIntl/QQi_wireless/Android/qqi_4.6.13.6034_office.apk";

		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		TransformerFactory.newInstance().newTransformer().transform(
				new SAXSource(new AndroidManifestReader(), new ApkInputSource(new URL(apk))), new DOMResult(document));
		System.out.println(document.getDocumentElement().getAttribute("android:versionCode"));

		StringWriter buffer = new StringWriter();
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(buffer));
		System.out.println(buffer.toString());
	}
}
