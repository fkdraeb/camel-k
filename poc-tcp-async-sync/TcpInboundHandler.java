/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// camel-k: language=java

import org.apache.camel.builder.RouteBuilder;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ACK;
import ca.uhn.hl7v2.model.v25.message.RSP_K21;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.QPD;
import ca.uhn.hl7v2.parser.PipeParser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.*;
import java.util.*;

public class TcpInboundHandler extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        /** POC Fluxo Sincrono (e.g. queries):
        *
        * Seamlink -> LIGHt
        *                  LIGHt query a BD do SONHO
        *                                              LIGHt responde ao requester (seamlink) com o resultado da query
        * */

        from("netty:tcp://0.0.0.0:8094")
        .routeId("tcp-sync-inbound-handler")
        .log("Message received in HL7 MLLP Inbound Route: \n${body}")
        .to("kamelet:inbound-sync-rabbitmq-sink");

        from("kamelet:inbound-sync-rabbitmq-source")
        .process(exchange -> {
            String body = exchange.getIn().getBody(String.class);

            Message originMessage = hl7ParserFromString(body);
            MSH mshSegment = (MSH) originMessage.get("MSH");
            QPD qpdSegment = (QPD) originMessage.get("QPD");

            String patientId = qpdSegment.getQpd3_UserParametersInsuccessivefields().encode().replaceAll(".*\\^", "");
            exchange.getIn().setHeader("patientId", patientId);
            exchange.getIn().setHeader("originalMshId", mshSegment.getMsh10_MessageControlID().getValue());

         })
        .to("sql:SELECT * FROM Patients WHERE patient_id LIKE :#${header.patientId} ?dataSource=#default")
        .process(exchange -> {
            String queryResultString = exchange.getIn().getBody(String.class);

            Map<String, String> queryResultMap = parseQueryResult(queryResultString);

            RSP_K21 responseMessage = new RSP_K21();
            responseMessage.initQuickstart("RSP", "K21", "D");
            responseMessage.getMSA().getAcknowledgmentCode().setValue("AA");
            responseMessage.getMSA().getMsa2_MessageControlID().setValue(exchange.getIn().getHeader("originalMshId").toString());

            if (queryResultMap.isEmpty()) {
                responseMessage.getMSA().getTextMessage().setValue("Utente não encontrado");
            } else {
                PID pid = responseMessage.getQUERY_RESPONSE().getPID();
                pid.getPid3_PatientIdentifierList(0).getIDNumber().setValue(queryResultMap.get("patient_id"));
                pid.getPid3_PatientIdentifierList(0).getCx4_AssigningAuthority().getHd1_NamespaceID().setValue("HOS");
                pid.getPid5_PatientName(0).getGivenName().setValue(queryResultMap.get("patient_full_name"));
                pid.getPid7_DateTimeOfBirth().getTime().setValue(dateTimeConverterToHL7Timestamp(queryResultMap.get("date_of_birth")));
                pid.getPid8_AdministrativeSex().setValue(queryResultMap.get("gender"));
                pid.getPid11_PatientAddress(0).getStreetAddress().getStreetOrMailingAddress().setValue(queryResultMap.get("patient_address"));
            }
            exchange.getIn().setBody(responseMessage.encode());
        });
        

        /** POC Fluxo Assincrono:
        *
        * Seamlink -> LIGHt
        *                  LIGHt adiciona a uma queue
        *                                              LIGHt responde ao requester (seamlink) com um ACK
        * */

        from("netty:tcp://0.0.0.0:8093")
        .routeId("tcp-async-inbound-handler")
        .log("Message received in HL7 MLLP Inbound Route: \n${body}")
        .to("kamelet:inbound-async-rabbitmq-sink")
        .process(exchange -> {
          ACK ack = new ACK();
          ack.initQuickstart("ACK", "A19", "D");
          ack.getMSH().getMsh10_MessageControlID().setValue(String.valueOf(UUID.randomUUID()));
          ack.getMSA().getAcknowledgmentCode().setValue("AA");
          ack.getMSA().getMsa2_MessageControlID().setValue(String.valueOf(UUID.randomUUID()));

          PipeParser parser = new PipeParser();
          String ackMessage = parser.encode(ack);
          exchange.getIn().setBody(ackMessage);
        })
        .log("ACK response sent");

    }
    
    public static Message hl7ParserFromString(String exchangeBody) throws HL7Exception {
        PipeParser parser = new PipeParser();
      return parser.parse(exchangeBody);
    }

    public static Map<String, String> parseQueryResult(String queryResultString) {
        Map<String, String> resultMap = new HashMap<>();

        Pattern pattern = Pattern.compile("\\{([^{}]+)\\}");
        Matcher matcher = pattern.matcher(queryResultString);

        while (matcher.find()) {
            String[] keyValuePairs = matcher.group(1).split(", ");
            for (String pair : keyValuePairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];
                    resultMap.put(key, value);
                }
            }
        }

        return resultMap;
    }

    private String dateTimeConverterToHL7Timestamp(String datetime) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date;
        try {
            date = dateFormat.parse(datetime);
        } catch (java.text.ParseException e) {
            System.err.println("Invalid date format");
            return "0";
        }
        SimpleDateFormat hl7DateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

        return hl7DateFormat.format(date);
    }
 
}
