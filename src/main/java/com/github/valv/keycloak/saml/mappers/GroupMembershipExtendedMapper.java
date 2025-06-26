package com.github.valv.keycloak.saml.mappers;

import org.keycloak.models.*;
import org.keycloak.protocol.*;
import org.keycloak.protocol.saml.SAMLLoginResponseBuilder;
import org.keycloak.protocol.saml.mappers.AbstractSAMLProtocolMapper;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.ConfigProperty;
import org.keycloak.representations.saml2.SAML2Object;
import org.keycloak.representations.saml2.assertion.AssertionType;
import org.keycloak.representations.saml2.assertion.AttributeStatementType;
import java.util.*;
import java.util.regex.Pattern;

public class GroupMembershipMapperExtended extends AbstractSAMLProtocolMapper implements SAMLLoginResponseBuilder.SAMLAttributeStatementMapper {

    public static final String PROVIDER_ID = "saml-group-membership-extended-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        configProperties.addAll(AbstractSAMLProtocolMapper.CONFIG_PROPERTIES);

        ConfigProperty patternProp = new ConfigProperty();
        patternProp.setName("groupPattern");
        patternProp.setLabel("Group Match Pattern");
        patternProp.setHelpText("Regex pattern to match group names (prefix/infix/suffix).");
        patternProp.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(patternProp);

        ConfigProperty stripProp = new ConfigProperty();
        stripProp.setName("stripMatch");
        stripProp.setLabel("Strip Matched Pattern");
        stripProp.setHelpText("Whether to strip the matched part of the group name before sending.");
        stripProp.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        configProperties.add(stripProp);
    }

    @Override
    public String getDisplayCategory() {
        return "SAML";
    }

    @Override
    public String getDisplayType() {
        return "Group Membership Extended";
    }

    @Override
    public String getHelpText() {
        return "Maps user groups to a SAML attribute with filtering and optional pattern stripping.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public void mapAttributeStatement(AttributeStatementType attributeStatement, ProtocolMapperModel mappingModel, KeycloakSession session,
                                      UserSessionModel userSession, ClientSessionContext clientSessionCtx) {
        String pattern = mappingModel.getConfig().getOrDefault("groupPattern", "");
        boolean strip = Boolean.parseBoolean(mappingModel.getConfig().getOrDefault("stripMatch", "false"));
        Pattern regex = pattern.isEmpty() ? null : Pattern.compile(pattern);

        List<String> groupNames = new ArrayList<>();
        userSession.getUser().getGroupsStream().forEach(group -> {
            String groupName = group.getName();
            if (regex != null) {
                var matcher = regex.matcher(groupName);
                if (matcher.find()) {
                    if (strip) {
                        groupName = matcher.replaceFirst("");
                    }
                    groupNames.add(groupName);
                }
            } else {
                groupNames.add(groupName);
            }
        });

        if (!groupNames.isEmpty()) {
            AttributeStatementType.ASTChoiceType attribute = new AttributeStatementType.ASTChoiceType();
            attribute.setAttribute(SAMLLoginResponseBuilder.createAttributeType(mappingModel.getConfig().getOrDefault("attribute.name", "groups"), groupNames));
            attributeStatement.addAttribute(attribute);
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    public static ProtocolMapperModel create(String name, String attributeName, String pattern, boolean strip) {
        ProtocolMapperModel mapper = new ProtocolMapperModel();
        mapper.setName(name);
        mapper.setProtocolMapper(PROVIDER_ID);
        mapper.setProtocol("saml");
        Map<String, String> config = new HashMap<>();
        config.put("attribute.name", attributeName);
        config.put("groupPattern", pattern);
        config.put("stripMatch", String.valueOf(strip));
        mapper.setConfig(config);
        return mapper;
    }
}

