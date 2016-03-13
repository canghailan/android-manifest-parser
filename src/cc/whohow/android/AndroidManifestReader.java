package cc.whohow.android;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <pre>
 * Binary AndroidManifest.xml
 * Magic Number(0x00080003)   4bytes
 * File Size                  4bytes
 * String Chunk
 *   Chunk Type(0x001C0001)   4bytes
 *   Chunk Size               4bytes
 *   String Count             4bytes
 *   Style Count              4bytes
 *   Unknown                  4bytes
 *   String Pool Offset       4bytes
 *   Style Pool Offset        4bytes
 *   String Offsets           4bytes * StringCount
 *   Style Offsets            4bytes * StyleCount
 *   String Pool *
 *     String Length          2bytes
 *     String Content         2bytes * StringLength
 *     \0                     2bytes
 *   Style Pool
 * ResourceId Chunk
 *   Chunk Type(0x00080180)   4bytes
 *   Chunk Size               4bytes
 *   ResourceIds              4bytes * (ChunkSize / 4 - 2)
 * XmlContent Chunk *
 *   Start Namespace Chunk
 *     Chunk Type(0x00100100) 4bytes
 *     Chunk Size             4bytes
 *     Line Number            4bytes
 *     Unknown                4bytes
 *     Prefix                 4bytes
 *     Uri                    4bytes
 *   End Namespace Chunk
 *     Chunk Type(0x00100101) 4bytes
 *     Chunk Size             4bytes
 *     Line Number            4bytes
 *     Unknown                4bytes
 *     Prefix                 4bytes
 *     Uri                    4bytes
 *   Start Tag Chunk
 *     Chunk Type(0x00100102) 4bytes
 *     Chunk Size             4bytes
 *     Line Number            4bytes
 *     Unknown                4bytes
 *     Namespace Uri          4bytes
 *     Name                   4bytes
 *     Flags                  4bytes
 *     Attribute Count        4bytes
 *     Class Attribute        4bytes
 *     Attributes *
 *       Namespace Uri        4bytes
 *       Name                 4bytes
 *       Value                4bytes
 *       Type                 4bytes
 *       Data                 4bytes
 *   End Tag Chunk
 *     Chunk Type(0x00100103) 4bytes
 *     Chunk Size             4bytes
 *     Line Number            4bytes
 *     Unknown                4bytes
 *     Namespace Uri          4bytes
 *     Name                   4bytes
 *   Text Chunk
 *     Chunk Type(0x00100104) 4bytes
 *     Chunk Size             4bytes
 *     Line Number            4bytes
 *     Unknown                4bytes
 *     Name                   4bytes
 *     Unknown                4bytes
 *     Unknown                4bytes
 * </pre>
 */
public class AndroidManifestReader implements XMLReader {
	public static final int MAGIC = 0x00080003;

	public static final int CHUNK_STRING = 0x001C0001;
	public static final int CHUNK_RESOURCE_ID = 0x00080180;
	public static final int CHUNK_START_NAMESPACE = 0x00100100;
	public static final int CHUNK_END_NAMESPACE = 0x00100101;
	public static final int CHUNK_START_TAG = 0x00100102;
	public static final int CHUNK_END_TAG = 0x00100103;
	public static final int CHUNK_TEXT = 0x00100104;

	private static final int ATTRIBUTE_VALUE_TYPE_ID_REF = 0x01000008;
	private static final int ATTRIBUTE_VALUE_TYPE_ATTR_REF = 0x02000008;
	private static final int ATTRIBUTE_VALUE_TYPE_STRING = 0x03000008;
	private static final int ATTRIBUTE_VALUE_TYPE_FLOAT = 0x04000008;
	private static final int ATTRIBUTE_VALUE_TYPE_DIMEN = 0x05000008;
	private static final int ATTRIBUTE_VALUE_TYPE_FRACTION = 0x06000008;
	private static final int ATTRIBUTE_VALUE_TYPE_INT = 0x10000008;
	private static final int ATTRIBUTE_VALUE_TYPE_FLAGS = 0x11000008;
	private static final int ATTRIBUTE_VALUE_TYPE_BOOL = 0x12000008;
	private static final int ATTRIBUTE_VALUE_TYPE_COLOR = 0x1C000008;
	private static final int ATTRIBUTE_VALUE_TYPE_COLOR2 = 0x1D000008;

	private static final String[] DIMEN = { "px", "dp", "sp", "pt", "in", "mm" };

	private EntityResolver resolver;
	private DTDHandler dtdHandler;
	private ContentHandler contentHandler;
	private ErrorHandler errorHandler;

	private Map<String, String> prefixMapping = new HashMap<>();
	private List<String> stringPool = new ArrayList<>();

	public void parse(InputStream stream) throws IOException, SAXException {
		// read stream
		try (ReadableByteChannel channel = Channels.newChannel(stream)) {
			ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
			while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
			}
			buffer.flip();
			int magic = buffer.getInt(); // Magic
			int fileSize = buffer.getInt(); // File Size
			buffer.rewind();
			assert magic == MAGIC;

			buffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN).put(buffer);
			while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
			}
			buffer.flip();
			
			parse(buffer);
		}
	}

	public void parse(ByteBuffer buffer) throws SAXException {
		buffer.order(ByteOrder.LITTLE_ENDIAN); // Ensure LITTLE_ENDIAN
		buffer.getInt(); // Magic
		buffer.getInt(); // File Size
		while (buffer.hasRemaining()) {
			buffer.mark();
			int chunkType = buffer.getInt(); // Chunk Type
			buffer.reset();
			switch (chunkType) {
			case CHUNK_STRING:
				parseStringChunk(buffer);
				break;
			case CHUNK_RESOURCE_ID:
				parseResourceIdChunk(buffer);
				break;
			case CHUNK_START_NAMESPACE:
				parseStartNamespaceChunk(buffer);
				break;
			case CHUNK_END_NAMESPACE:
				parseEndNamespaceChunk(buffer);
				break;
			case CHUNK_START_TAG:
				parseStartTagChunk(buffer);
				break;
			case CHUNK_END_TAG:
				parseEndTagChunk(buffer);
				break;
			case CHUNK_TEXT:
				parseTextChunk(buffer);
				break;
			default:
				assert false;
			}
		}
	}

	private String getString(int index) {
		return index < 0 ? null : stringPool.get(index);
	}

	private String getUri(int index) {
		String uri = getString(index);
		return uri == null ? "" : uri;
	}

	private String getLocalName(int index) {
		return getString(index);
	}

	private String getQName(String uri, String localName) {
		if (uri.isEmpty()) {
			return localName;
		}
		return prefixMapping.get(uri) + ":" + localName;
	}

	private void parseStringChunk(ByteBuffer buffer) {
		int chunkOffset = buffer.position();

		buffer.getInt(); // Chunk Type
		int chunkSize = buffer.getInt(); // Chunk Size
		int stringCount = buffer.getInt(); // String Count
		buffer.getInt(); // Style Count
		buffer.getInt(); // Unknown
		int stringPoolOffset = buffer.getInt(); // String Pool Offset
		buffer.getInt(); // Style Pool Offset
		int[] stringOffsets = new int[stringCount]; // String Offsets
		for (int i = 0; i < stringOffsets.length; i++) {
			stringOffsets[i] = buffer.getInt();
		}
		// Style Offsets
		// String Pool
		for (int i = 0; i < stringOffsets.length; i++) {
			parseString(chunkOffset + stringPoolOffset + stringOffsets[i], buffer);
		}
		// Style Pool

		buffer.position(chunkOffset + chunkSize);
	}

	private void parseString(int offset, ByteBuffer buffer) {
		buffer.position(offset);
		short stringLength = buffer.getShort(); // String Length
		byte[] string = new byte[stringLength * 2]; // UTF16 LE
		buffer.get(string);
		buffer.getShort(); // 00 00
		stringPool.add(new String(string, StandardCharsets.UTF_16LE));
	}

	private void parseResourceIdChunk(ByteBuffer buffer) {
		// Skip
		int chunkOffset = buffer.position();
		buffer.getInt(); // Chunk Type
		int chunkSize = buffer.getInt(); // Chunk Size
		buffer.position(chunkOffset + chunkSize);
	}

	private void parseStartNamespaceChunk(ByteBuffer buffer) throws SAXException {
		buffer.getInt(); // Chunk Type
		buffer.getInt(); // Chunk Size
		buffer.getInt(); // Line Number
		buffer.getInt(); // Unknown
		int prefix = buffer.getInt(); // Prefix
		int uri = buffer.getInt(); // Uri

		prefixMapping.put(getString(uri), getString(prefix));
		contentHandler.startPrefixMapping(getString(prefix), getString(uri));
	}

	private void parseEndNamespaceChunk(ByteBuffer buffer) throws SAXException {
		buffer.getInt(); // Chunk Type
		buffer.getInt(); // Chunk Size
		buffer.getInt(); // Line Number
		buffer.getInt(); // Unknown
		int prefix = buffer.getInt(); // Prefix
		buffer.getInt(); // Uri

		contentHandler.endPrefixMapping(getString(prefix));
	}

	private void parseStartTagChunk(ByteBuffer buffer) throws SAXException {
		buffer.getInt(); // Chunk Type
		buffer.getInt(); // Chunk Size
		buffer.getInt(); // Line Number
		buffer.getInt(); // Unknown
		int namespaceUri = buffer.getInt(); // Namespace Uri
		int name = buffer.getInt(); // Name
		buffer.getInt(); // Flags
		int attributeCount = buffer.getInt(); // Attribute Count
		buffer.getInt(); // Class Attribute
		// Attributes
		AttributesImpl attributes = new AttributesImpl();
		for (int i = 0; i < attributeCount; i++) {
			parseAttribute(buffer, attributes);
		}

		String uri = getUri(namespaceUri);
		String localName = getLocalName(name);
		String qName = getQName(uri, localName);
		contentHandler.startElement(uri, localName, qName, attributes);
	}

	private void parseAttribute(ByteBuffer buffer, AttributesImpl attributes) {
		int namespaceUri = buffer.getInt(); // Namespace Uri
		int name = buffer.getInt(); // Name
		int string = buffer.getInt(); // Value String
		int type = buffer.getInt(); // Type

		String attributeValue;
		if (type == ATTRIBUTE_VALUE_TYPE_STRING) {
			buffer.getInt(); // Data
			attributeValue = getString(string);
		} else {
			attributeValue = parseAttributeValue(type, buffer);
		}

		String uri = getUri(namespaceUri);
		String localName = getLocalName(name);
		String qName = getQName(uri, localName);
		attributes.addAttribute(uri, localName, qName, "CDATA", attributeValue);
	}

	private String parseAttributeValue(int type, ByteBuffer buffer) {
		switch (type) {
		case ATTRIBUTE_VALUE_TYPE_ID_REF: {
			return String.format("@id/0x%08X", buffer.getInt());
		}
		case ATTRIBUTE_VALUE_TYPE_ATTR_REF: {
			return String.format("?id/0x%08X", buffer.getInt());
		}
		case ATTRIBUTE_VALUE_TYPE_FLOAT: {
			return Float.toString(buffer.getFloat());
		}
		case ATTRIBUTE_VALUE_TYPE_DIMEN: {
			int data = buffer.getInt();
			return Integer.toString(data >> 8) + DIMEN[data & 0xFF];
		}
		case ATTRIBUTE_VALUE_TYPE_FRACTION: {
			return String.format("%.2f%%", buffer.getFloat());
		}
		case ATTRIBUTE_VALUE_TYPE_INT: {
			return Integer.toString(buffer.getInt());
		}
		case ATTRIBUTE_VALUE_TYPE_FLAGS: {
			return String.format("0x%08X", buffer.getInt());
		}
		case ATTRIBUTE_VALUE_TYPE_BOOL: {
			return Boolean.toString(buffer.getInt() != 0);
		}
		case ATTRIBUTE_VALUE_TYPE_COLOR:
		case ATTRIBUTE_VALUE_TYPE_COLOR2: {
			return String.format("#%08X", buffer.getInt());
		}
		default: {
			return String.format("%08X/0x%08X", type, buffer.getInt());
		}
		}
	}

	private void parseEndTagChunk(ByteBuffer buffer) throws SAXException {
		buffer.getInt(); // Chunk Type
		buffer.getInt(); // Chunk Size
		buffer.getInt(); // Line Number
		buffer.getInt(); // Unknown
		int namespaceUri = buffer.getInt(); // Namespace Uri
		int name = buffer.getInt(); // Name

		String uri = getUri(namespaceUri);
		String localName = getLocalName(name);
		String qName = getQName(uri, localName);
		contentHandler.endElement(uri, localName, qName);
	}

	private void parseTextChunk(ByteBuffer buffer) throws SAXException {
		buffer.getInt(); // Chunk Type
		buffer.getInt(); // Chunk Size
		buffer.getInt(); // Line Number
		buffer.getInt(); // Unknown
		int name = buffer.getInt(); // Name
		buffer.getInt(); // Unknown
		buffer.getInt(); // Unknown

		char[] text = getString(name).toCharArray();
		contentHandler.characters(text, 0, text.length);
	}

	@Override
	public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
		return false;
	}

	@Override
	public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
	}

	@Override
	public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
		return null;
	}

	@Override
	public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
	}

	@Override
	public void setEntityResolver(EntityResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public EntityResolver getEntityResolver() {
		return resolver;
	}

	@Override
	public void setDTDHandler(DTDHandler handler) {
		this.dtdHandler = handler;
	}

	@Override
	public DTDHandler getDTDHandler() {
		return dtdHandler;
	}

	@Override
	public void setContentHandler(ContentHandler handler) {
		this.contentHandler = handler;
	}

	@Override
	public ContentHandler getContentHandler() {
		return contentHandler;
	}

	@Override
	public void setErrorHandler(ErrorHandler handler) {
		this.errorHandler = handler;
	}

	@Override
	public ErrorHandler getErrorHandler() {
		return errorHandler;
	}

	@Override
	public void parse(InputSource input) throws IOException, SAXException {
		parse(input.getByteStream());
	}

	@Override
	public void parse(String systemId) throws IOException, SAXException {
		throw new UnsupportedOperationException();
	}
}
