package fi.nls.oskari.util;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import org.apache.axiom.om.*;
import org.geotools.data.DataUtilities;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.gml.producer.FeatureTransformer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeatureType;
import org.geotools.geometry.jts.WKTReader2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class GeoServerRequestBuilder {

    private static final Logger log = LogFactory.getLogger(GeoServerRequestBuilder.class);

    private static final String VERSION_1_1_0 = "1.1.0";
    private static final String VERSION_1_0_0 = "1.0.0";

    private static OMFactory factory = null;
    private static OMNamespace xmlSchemaInstance = null;
    private static OMNamespace wfsNameSpace = null;

    public GeoServerRequestBuilder() {

        factory = OMAbstractFactory.getOMFactory();

        xmlSchemaInstance = factory.createOMNamespace("http://www.w3.org/2001/XMLSchema-instance", "xsi");
        wfsNameSpace = factory.createOMNamespace("http://www.opengis.net/wfs", "wfs");
    }

    private static final List<String> LAYERS_LIST = Arrays.asList("category_name", "default", "stroke_width",
            "stroke_color", "fill_color", "uuid", "dot_color", "dot_size", "border_width", "border_color",
            "dot_shape", "stroke_linejoin", "fill_pattern", "stroke_linecap", "stroke_dasharray", "border_linejoin",
            "border_dasharray");


    private static final List<String> FEATURES_LIST = Arrays.asList("name", "place_desc", "attention_text", "link",
            "image_url", "category_id", "feature");

    public OMElement buildLayersGet(String uuid) {

        OMElement root = null;

        try {
            root = buildWFSRootNode("GetFeature", VERSION_1_1_0);

            OMElement query = factory.createOMElement("Query", wfsNameSpace);
            OMAttribute typeName = factory.createOMAttribute("typeName", null, "feature:categories");
            OMAttribute srsName = factory.createOMAttribute("srsName", null, "EPSG:3067");
            query.addAttribute(typeName);
            query.addAttribute(srsName);

            OMNamespace ogc = factory.createOMNamespace("http://www.opengis.net/ogc", "ogc");
            OMElement filter = factory.createOMElement("Filter", ogc);

            OMElement propertyIsEqualTo = factory.createOMElement("PropertyIsEqualTo", ogc);

            OMAttribute matchCase = factory.createOMAttribute("matchCase", null, "true");
            propertyIsEqualTo.addAttribute(matchCase);

            OMElement property = factory.createOMElement("PropertyName", ogc);
            property.setText("uuid");
            propertyIsEqualTo.addChild(property);

            OMElement literal = factory.createOMElement("Literal", ogc);
            literal.setText(uuid);
            propertyIsEqualTo.addChild(literal);

            filter.addChild(propertyIsEqualTo);
            query.addChild(filter);
            root.addChild(query);
        }
        catch (Exception e){
            log.error(e, "Failed to create payload - root: ", root);
            throw new RuntimeException(e.getMessage());
        }

        return root;
    }

    public OMElement buildLayersInsert(String payload) {

        OMElement root = null;

        try {
            root = buildWFSRootNode("Transaction", VERSION_1_1_0);

            OMElement transaction = factory.createOMElement("Insert", wfsNameSpace);
            OMNamespace feature = factory.createOMNamespace("http://www.oskari.org", "feature");

            OMElement categories = factory.createOMElement("categories", feature);

            JSONArray jsonArray = new JSONObject(payload).getJSONArray("categories");
            for (int i = 0; i < jsonArray.length(); ++i) {
                for (String property : LAYERS_LIST) {
                    transaction.addChild(getElement(jsonArray.getJSONObject(i), property, feature));
                }
            }

            transaction.addChild(categories);
            root.addChild(transaction);
        }
        catch (Exception e){
            log.error(e, "Failed to create payload - root: ", root);
            throw new RuntimeException(e.getMessage());
        }
        return root;
    }

    public OMElement buildLayersUpdate(String payload) {

        OMElement root = null;

        try {
            root = buildWFSRootNode("Transaction", VERSION_1_1_0);

            OMNamespace feature = factory.createOMNamespace("http://www.oskari.org", "feature");
            OMElement transaction = factory.createOMElement("Update", feature);
            OMAttribute typeName = factory.createOMAttribute("typeName", null, "feature:categories");
            transaction.addAttribute(typeName);

            JSONArray jsonArray = new JSONObject(payload).getJSONArray("categories");
            for (int i = 0; i < jsonArray.length(); ++i) {
                for (String property : LAYERS_LIST) {
                    transaction.addChild(buildPropertyElement(jsonArray.getJSONObject(i), property, feature));
                }
                transaction.addChild(buildCategoryIdFilter(jsonArray.getJSONObject(i).getString("category_id")));
            }
            root.addChild(transaction);
        }
        catch (Exception e){
            log.error(e, "Failed to create payload - root: ", root);
            throw new RuntimeException(e.getMessage());
        }
        return root;
    }


    public OMElement buildLayersDelete(String categoryId) {

        OMElement root = null;

        try {
            root = buildWFSRootNode("Transaction", VERSION_1_1_0);

            OMNamespace feature = factory.createOMNamespace("http://www.oskari.org", "feature");
            OMElement transaction = factory.createOMElement("Delete", feature);
            OMAttribute typeName = factory.createOMAttribute("typeName", null, "feature:categories");
            transaction.addAttribute(typeName);

            transaction.addChild(buildCategoryIdFilter(categoryId));

            root.addChild(transaction);
        }
        catch (Exception e){
            log.error(e, "Failed to create payload - root: ", root);
            throw new RuntimeException(e.getMessage());
        }

        return root;

    }

    private OMElement buildWFSRootNode (String wfsType, String version) throws Exception {
        OMElement root = factory.createOMElement(wfsType, wfsNameSpace);
        OMAttribute schemaLocation = factory.createOMAttribute("schemaLocation",
                xmlSchemaInstance,
                "http://www.opengis.net/wfs http://schemas.opengis.net/wfs/"
                        + VERSION_1_1_0 + "/wfs.xsd");

        OMAttribute versionElement = factory.createOMAttribute("version", null, version);
        OMAttribute serviceElement = factory.createOMAttribute("service", null, "WFS");

        root.addAttribute(schemaLocation);
        root.addAttribute(versionElement);
        root.addAttribute(serviceElement);

        return root;
    }

    private OMElement getElement(JSONObject jsonObject, String fieldName, OMNamespace feature) throws Exception {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMElement var = null;
        try {
            String value = jsonObject.getString(fieldName);
            var = factory.createOMElement(fieldName, feature);
            var.setText(value);
        }
        catch (Exception e) {
            throw new Exception(e.getMessage());
        }
        return var;
    }

    private OMElement buildPropertyElement(JSONObject jsonObject, String fieldName, OMNamespace feature) throws Exception {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMElement property = null;
        try {
            property = factory.createOMElement("Property", feature);

            OMElement propertyName = factory.createOMElement("Name", feature);
            propertyName.setText(fieldName);
            property.addChild(propertyName);

            String value = jsonObject.getString(fieldName);
            OMElement propertyValue = factory.createOMElement("Value", feature);
            propertyValue.setText(value);
            property.addChild(propertyValue);
        }
        catch (Exception e) {
            throw new Exception(e.getMessage());
        }
        return property;
    }

    private OMElement buildCategoryIdFilter(String categoryId) {
        OMFactory factory = OMAbstractFactory.getOMFactory();

        OMNamespace ogc = factory.createOMNamespace("http://www.opengis.net/ogc", "ogc");
        OMElement filter = factory.createOMElement("Filter", ogc);

        OMElement property = factory.createOMElement("FeatureId", ogc);
        OMAttribute idAttribute = factory.createOMAttribute("fid", null, categoryId);
        property.addAttribute(idAttribute);

        filter.addChild(property);

        return filter;
    }

    public OMElement buildFeaturesGet(String payload) {
        return null;
    }

    public OMElement buildFeaturesInsert(String payload) throws Exception {

        OMElement root = null;

        try {
            root = buildWFSRootNode("Transaction", VERSION_1_0_0);

            OMElement transaction = factory.createOMElement("Insert", wfsNameSpace);
            OMNamespace feature = factory.createOMNamespace("http://www.oskari.org", "feature");

            OMElement myPlaces = factory.createOMElement("my_places", feature);
            myPlaces.addChild(getGeometry());

            JSONArray jsonArray = new JSONObject(payload).getJSONArray("features");
            for (int i = 0; i < jsonArray.length(); ++i) {
                for (String property : FEATURES_LIST) {
                    myPlaces.addChild(getElement(jsonArray.getJSONObject(i).getJSONObject("properties"), property, feature));
                }
            }

            transaction.addChild(myPlaces);
            root.addChild(transaction);
        } catch (Exception e) {
            log.error(e, "Failed to create payload - root: ", root);
            throw new RuntimeException(e.getMessage());
        }
        return root;
    }

    private OMElement getGeometry() throws Exception {

        SimpleFeatureType GEOMETRY_TYPE = DataUtilities.createType("Location", "geom:Point,name:String");

        DefaultFeatureCollection collection = new DefaultFeatureCollection();
        WKTReader2 wkt = new WKTReader2();
        collection.add(SimpleFeatureBuilder.build(GEOMETRY_TYPE, new Object[] { wkt.read("POINT (1 2)"),
                "name1" }, null));

        FeatureTransformer transform = new FeatureTransformer();
        transform.setEncoding(StandardCharsets.UTF_8);
        transform.setGmlPrefixing(true);

        // define feature information
        transform.getFeatureTypeNamespaces().declareDefaultNamespace("feature", "http://www.opengis.net/wfs");
        transform.addSchemaLocation("schemaLocation", "http://www.opengis.net/wfs http://schemas.opengis.net/wfs/"
                + VERSION_1_0_0 + "/wfs.xsd");
        transform.setSrsName("EPSG:3076");

        ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
        transform.transform(collection, xmlOutput);

        InputStream xmlInput = new ByteArrayInputStream(xmlOutput.toByteArray());
        OMElement root = OMXMLBuilderFactory.createOMBuilder(xmlInput).getDocumentElement();
        return root;
    }

    public OMElement buildFeaturesUpdate(String payload) {
        return null;
    }

    public OMElement buildFeaturesDelete(String payload) {
        return null;
    }
}