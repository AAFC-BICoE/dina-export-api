<?xml version="1.0" encoding="UTF-8"?>
<archive xmlns="http://rs.tdwg.org/dwc/text/" metadata="eml.xml">
  <core encoding="UTF-8" rowType="${rowType}"
    fieldsTerminatedBy="," linesTerminatedBy="\n" quoteCharacter="&quot;" ignoreHeaderLines="1">
    <files>
      <#list fileLocations as loc><location>${loc}</location>
      </#list>
    </files>
    <id index="0"/>
    <#list columns as column>
    <field index="${column?index}" term="${column.uri}" <#if column.dataType??> datatype="${column.dataType}"</#if>/>
    </#list>
  </core>
</archive>