package com.github.valv.keycloak.saml.mappers;

import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.protocol.saml.SamlProtocol;
import org.keycloak.protocol.saml.mappers.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.jboss.logging.Logger;  // Keycloak's logging framework

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class GroupMembershipExtendedMapper extends AbstractSAMLProtocolMapper implements SAMLAttributeStatementMapper {
    public static final String PROVIDER_ID = "saml-group-membership-extended-mapper";
    public static final String SINGLE_GROUP_ATTRIBUTE = "single";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();
    private static final Logger logs = Logger.getLogger(GroupMembershipExtendedMapper.class);

    static {
        ProviderConfigProperty property;

        property = new ProviderConfigProperty();
        property.setName(AttributeStatementHelper.SAML_ATTRIBUTE_NAME);
        property.setLabel("Group attribute name");
        property.setDefaultValue("member");
        property.setHelpText("Name of the SAML attribute you want to put your groups into.  i.e. 'member', 'memberOf'.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(AttributeStatementHelper.FRIENDLY_NAME);
        property.setLabel(AttributeStatementHelper.FRIENDLY_NAME_LABEL);
        property.setHelpText(AttributeStatementHelper.FRIENDLY_NAME_HELP_TEXT);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(AttributeStatementHelper.SAML_ATTRIBUTE_NAMEFORMAT);
        property.setLabel("SAML Attribute NameFormat");
        property.setHelpText("SAML Attribute NameFormat.  Can be basic, URI reference, or unspecified.");
        List<String> types = new ArrayList<String>(3);
        types.add(AttributeStatementHelper.BASIC);
        types.add(AttributeStatementHelper.URI_REFERENCE);
        types.add(AttributeStatementHelper.UNSPECIFIED);
        property.setType(ProviderConfigProperty.LIST_TYPE);
        property.setOptions(types);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("pattern.match");
        property.setLabel("Group pattern match");
        property.setDefaultValue("saml-");
        property.setHelpText("Allow only groups that match this pattern to be included into SAML assertion.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("pattern.strip");
        property.setLabel("Group pattern strip");
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        property.setDefaultValue("true");
        property.setHelpText("If true, matched pattern will be removed from the group name in the output SAML assertion.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(SINGLE_GROUP_ATTRIBUTE);
        property.setLabel("Single Group Attribute");
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        property.setDefaultValue("true");
        property.setHelpText("If true, all groups will be stored under one attribute with multiple attribute values.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("full.path");
        property.setLabel("Full group path");
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        property.setDefaultValue("true");
        property.setHelpText("Include full path to group i.e. /top/level1/level2, false will just specify the group name");
        configProperties.add(property);
    }

    @Override
    public String getDisplayCategory() {
        return "Group Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Group list extended";
    }

    @Override
    public String getHelpText() {
        return "Group names are stored in an attribute value.  There is either one attribute with multiple attribute values, or an attribute per group name depending on how you configure it.  You can also specify the attribute name i.e. 'member' or 'memberOf' being examples.  In this extended version you can filter groups by pattern and optionally strip the pattern from the group name.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    public static boolean useFullPath(ProtocolMapperModel mappingModel) {
        return "true".equals(mappingModel.getConfig().get("full.path"));
    }

    public static boolean stripPattern(ProtocolMapperModel mappingModel) {
        return "true".equals(mappingModel.getConfig().get("pattern.strip"));
    }

    @Override
    public void transformAttributeStatement(AttributeStatementType attributeStatement, ProtocolMapperModel mappingModel, KeycloakSession session, UserSessionModel userSession, AuthenticatedClientSessionModel clientSession) {
        String single = mappingModel.getConfig().get(SINGLE_GROUP_ATTRIBUTE);
        boolean singleAttribute = Boolean.parseBoolean(single);

        boolean fullPath = useFullPath(mappingModel);
        boolean patternStrip = stripPattern(mappingModel);
        String patternMatch = mappingModel.getConfig().get("pattern.match");
        logs.debugf("Filtering groups by pattern: %s (stripping = %b)", patternMatch, patternStrip);

        final AtomicReference<AttributeType> singleAttributeType = new AtomicReference<>(null);

        userSession.getUser().getGroupsStream().forEach(group -> {
            String groupName;
            if (fullPath) {
                groupName = ModelToRepresentation.buildGroupPath(group);
            } else {
                groupName = group.getName();
            }

            if (patternMatch != null && !patternMatch.isEmpty() && !groupName.contains(patternMatch)) {
                logs.debugf("Skipping %s as non-matching", groupName);
                return;
            }

            AttributeType attributeType;
            if (singleAttribute) {
                if (singleAttributeType.get() == null) {
                    singleAttributeType.set(AttributeStatementHelper.createAttributeType(mappingModel));
                    attributeStatement.addAttribute(new AttributeStatementType.ASTChoiceType(singleAttributeType.get()));
                }
                attributeType = singleAttributeType.get();
            } else {
                attributeType = AttributeStatementHelper.createAttributeType(mappingModel);
                attributeStatement.addAttribute(new AttributeStatementType.ASTChoiceType(attributeType));
            }

            if (patternStrip) {
                groupName = groupName.replaceAll(patternMatch, "");
                logs.debugf("Stripped group name: %s", groupName);
            }

            attributeType.addAttributeValue(groupName);
        });
    }

    public static ProtocolMapperModel create(String name, String samlAttributeName, String nameFormat, String friendlyName, boolean singleAttribute) {
        ProtocolMapperModel mapper = new ProtocolMapperModel();

        mapper.setName(name);
        mapper.setProtocolMapper(PROVIDER_ID);
        mapper.setProtocol(SamlProtocol.LOGIN_PROTOCOL);

        Map<String, String> config = new HashMap<String, String>();

        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME, samlAttributeName);

        if (friendlyName != null) {
            config.put(AttributeStatementHelper.FRIENDLY_NAME, friendlyName);
        }

        if (nameFormat != null) {
            config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAMEFORMAT, nameFormat);
        }

        config.put(SINGLE_GROUP_ATTRIBUTE, Boolean.toString(singleAttribute));

        mapper.setConfig(config);

        return mapper;
    }

}
