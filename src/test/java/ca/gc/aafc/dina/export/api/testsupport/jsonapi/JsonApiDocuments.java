package ca.gc.aafc.dina.export.api.testsupport.jsonapi;

import java.util.UUID;

public class JsonApiDocuments {

  private static final String MAT_SAMPLE_DOCUMENT = """
    {
        "data" : {
          "id" : "%s",
          "type" : "material-sample",
          "attributes" : {
            "version" : 3,
            "group" : "cnc",
            "createdOn" : "2022-02-11T21:50:15.325043Z",
            "createdBy" : "cnc-cm",
            "dwcCatalogNumber" : "cn,1",
            "dwcOtherCatalogNumbers" : ["cn1","cn1-1"],
            "materialSampleName" : "Yves",
            "materialSampleChildren" : [ ],
            "preparationDate" : null,
            "managedAttributes" : { "attribute_1":"value 1"},
            "determination" : [
              {
                "verbatimScientificName" : "Yves computer science",
                "verbatimDeterminer" : "Yves",
                "verbatimDate" : null,
                "scientificName" : null,
                "transcriberRemarks" : null,
                "verbatimRemarks" : null,
                "determinationRemarks" : null,
                "typeStatus" : null,
                "typeStatusEvidence" : null,
                "determiner" : null,
                "determinedOn" : null,
                "qualifier" : null,
                "scientificNameSource" : null,
                "scientificNameDetails" : null,
                "isPrimary" : true,
                "isFileAs" : true,
                "managedAttributes" : null
              }
            ],
            "preparationRemarks" : null,
            "dwcDegreeOfEstablishment" : null,
            "host" : null,
            "barcode" : null,
            "publiclyReleasable" : true,
            "notPubliclyReleasableReason" : null,
            "tags" : null,
            "materialSampleState" : null,
            "materialSampleRemarks" : null,
            "stateChangedOn" : null,
            "stateChangeRemarks" : null,
            "preparationMethod" : null,
            "associations" : [ ],
            "allowDuplicateName" : false
          },
          "relationships" : {
            "collectingEvent" : {
               "data": {
                 "id": "01900d5a-dc89-7adc-b1cd-bc99ef3d910d",
                 "type": "collecting-event"
               }
            },
            "storageUnit" : {
              "data" : null
            }
          }
        },
        "included":
         [
              {
                "id": "01900d5a-dc89-7adc-b1cd-bc99ef3d910d",
                "type": "collecting-event",
                "attributes": {
                  "group": "cnc",
                  "dwcVerbatimLocality" : "Montreal",
                  "managedAttributes" : { "attribute_ce_1":"value ce 1"}
                }
              }
         ],
        "meta" : {
          "totalResourceCount" : 1,
          "moduleVersion" : "0.47"
        }
      }
    """;

  public static String getMaterialSampleDocument() {
    return String.format(MAT_SAMPLE_DOCUMENT, UUID.randomUUID());
  }
  public static String getMaterialSampleDocument(UUID documentId) {
    return String.format(MAT_SAMPLE_DOCUMENT, documentId);
  }

}
