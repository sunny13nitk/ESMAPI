package com.sap.cap.esmapi.utilities.srv.impl;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cap.esmapi.exceptions.EX_ESMAPI;
import com.sap.cap.esmapi.utilities.constants.GC_Constants;
import com.sap.cap.esmapi.utilities.pojos.TY_AccountCreate;
import com.sap.cap.esmapi.utilities.pojos.TY_CaseESS;
import com.sap.cap.esmapi.utilities.pojos.TY_DefaultComm;
import com.sap.cap.esmapi.utilities.pojos.TY_SrvCloudUrls;
import com.sap.cap.esmapi.utilities.pojos.TY_UserESS;
import com.sap.cap.esmapi.utilities.pojos.Ty_UserAccountContact;
import com.sap.cap.esmapi.utilities.srv.intf.IF_APISrv;
import com.sap.cap.esmapi.utilities.srv.intf.IF_UserAPISrv;
import com.sap.cloud.security.xsuaa.token.Token;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;


@Service
@Scope(value = "session", proxyMode=ScopedProxyMode.TARGET_CLASS)
public class CL_UserAPISrv implements IF_UserAPISrv
{
    private Ty_UserAccountContact userData;
    
    @Autowired
    private MessageSource msgSrc;

    @Autowired
    private TY_SrvCloudUrls srvCloudUrls;

    @Autowired
    private IF_APISrv apiSrv;

   

    @Override
    public Ty_UserAccountContact getUserDetails(@AuthenticationPrincipal Token token) throws EX_ESMAPI 
    {
        if(token == null)
        {
            throw new EX_ESMAPI(msgSrc.getMessage("NO_TOKEN", null, Locale.ENGLISH));
        }

        else
        {
            //Return from Session if Populated else make some effort
            if(userData == null)
            {
                //Fetch and Return
                this.userData = new Ty_UserAccountContact();
                userData.setUserId(token.getLogonName());
                userData.setUserName(token.getGivenName() + " " + token.getFamilyName());
                userData.setUserEmail(token.getEmail());
                userData.setAccountId(getAccountIdByUserEmail(userData.getUserEmail()));
                userData.setContactId(getContactPersonIdByUserEmail(userData.getUserEmail()));
                 
            }
        }
        return userData;
        
    }

    @Override
    public String getAccountIdByUserEmail(String userEmail) throws EX_ESMAPI 
    {
        String accountID = null;
        Map<String,String> accEmails = new HashMap<String,String>();
        if (StringUtils.hasText(userEmail) && srvCloudUrls != null)
        {
            if (StringUtils.hasText(srvCloudUrls.getAccountsUrl())) 
            {
                try 
                {
                    JsonNode accountsResp = getAllAccounts();
                    if (accountsResp != null)
                     {
                        JsonNode rootNode = accountsResp.path("value");
                        if (rootNode != null)
                        {
                            System.out.println("Accounts Bound!!");

                            Iterator<Map.Entry<String, JsonNode>> payloadItr = accountsResp.fields();
                            while (payloadItr.hasNext()) 
                            {
                                System.out.println("Payload Iterator Bound");
                                Map.Entry<String, JsonNode> payloadEnt = payloadItr.next();
                                String payloadFieldName = payloadEnt.getKey();
                                System.out.println("Payload Field Scanned:  " + payloadFieldName);

                                if (payloadFieldName.equals("value")) 
                                {
                                    Iterator<JsonNode> accItr = payloadEnt.getValue().elements();
                                    System.out.println("Accounts Iterator Bound");
                                    while (accItr.hasNext()) 
                                    {

                                        JsonNode accEnt = accItr.next();
                                        if (accEnt != null) 
                                        {
                                            String accid = null, accEmail = null;
                                            System.out.println("Account Entity Bound - Reading Account...");
                                            Iterator<String> fieldNames = accEnt.fieldNames();
                                            while (fieldNames.hasNext()) 
                                            {
                                                String accFieldName = fieldNames.next();
                                                System.out.println("Account Entity Field Scanned:  " + accFieldName);
                                                if (accFieldName.equals("id")) 
                                                {
                                                    System.out.println(
                                                            "Account Id Added : " + accEnt.get(accFieldName).asText());
                                                    accid = accEnt.get(accFieldName).asText();
                                                }

                                                if (accFieldName.equals("defaultCommunication")) 
                                                {
                                                    System.out.println("Inside Default Communication:  " );

                                                    JsonNode commEnt = accEnt.path("defaultCommunication");
                                                    if(commEnt != null)
                                                    {
                                                        System.out.println("Comm's Node Bound");

                                                        Iterator<String> fieldNamesComm = commEnt.fieldNames();
                                                        while (fieldNamesComm.hasNext()) 
                                                        {
                                                            String commFieldName = fieldNamesComm.next();
                                                            if (commFieldName.equals("eMail")) 
                                                                {
                                                                    System.out.println(
                                                                            "Account Email Added : " + commEnt.get(commFieldName).asText());
                                                                    accEmail = commEnt.get(commFieldName).asText();
                                                                }
                                                        }

                                                    }
                                                }

                                            }
                                            //avoid null email accounts
                                            if(StringUtils.hasText(accid) && StringUtils.hasText(accEmail))
                                            {
                                                accEmails.put(accid,accEmail);
                                            }

                                        }


                                    }

                                }

                            }

                            //Filter by Email
                           Optional<Map.Entry<String,String>> OptionalAcc =  accEmails.entrySet().stream().filter(u->u.getValue().equals(userEmail)).findFirst();
                           if(OptionalAcc.isPresent())
                           {
                                Map.Entry<String,String> account = OptionalAcc.get();
                                accountID = account.getKey(); //Return Account ID
                           }
                           



                        }
                    }
                } catch (IOException e)
                {
                    throw new EX_ESMAPI(msgSrc.getMessage("API_AC_ERROR", new Object[] { e.getLocalizedMessage() },
                            Locale.ENGLISH));
                }
            }

        }
        return accountID;
    }



    @Override
    public String getContactPersonIdByUserEmail(String userEmail) throws EX_ESMAPI 
    {
        String accountID = null;
        Map<String,String> accEmails = new HashMap<String,String>();
        if (StringUtils.hasText(userEmail) && srvCloudUrls != null)
        {
            if (StringUtils.hasText(srvCloudUrls.getCpUrl())) 
            {
                try 
                {
                    JsonNode accountsResp = getAllContacts();
                    if (accountsResp != null)
                     {
                        JsonNode rootNode = accountsResp.path("value");
                        if (rootNode != null)
                        {
                            System.out.println("Contacts Bound!!");

                            Iterator<Map.Entry<String, JsonNode>> payloadItr = accountsResp.fields();
                            while (payloadItr.hasNext()) 
                            {
                                System.out.println("Payload Iterator Bound");
                                Map.Entry<String, JsonNode> payloadEnt = payloadItr.next();
                                String payloadFieldName = payloadEnt.getKey();
                                System.out.println("Payload Field Scanned:  " + payloadFieldName);

                                if (payloadFieldName.equals("value")) 
                                {
                                    Iterator<JsonNode> accItr = payloadEnt.getValue().elements();
                                    System.out.println("Contacts Iterator Bound");
                                    while (accItr.hasNext()) 
                                    {

                                        JsonNode accEnt = accItr.next();
                                        if (accEnt != null) 
                                        {
                                            String accid = null, accEmail = null;
                                            System.out.println("Contact Entity Bound - Reading Contact...");
                                            Iterator<String> fieldNames = accEnt.fieldNames();
                                            while (fieldNames.hasNext()) 
                                            {
                                                String accFieldName = fieldNames.next();
                                                System.out.println("Contact Entity Field Scanned:  " + accFieldName);
                                                if (accFieldName.equals("id")) 
                                                {
                                                    System.out.println(
                                                            "Account Id Added : " + accEnt.get(accFieldName).asText());
                                                    accid = accEnt.get(accFieldName).asText();
                                                }

                                                if (accFieldName.equals("eMail")) 
                                                {
                                                    System.out.println(
                                                            "Account Email Added : " + accEnt.get(accFieldName).asText());
                                                    accEmail =  accEnt.get(accFieldName).asText();
                                                }

                                               

                                            }
                                            //avoid null email accounts
                                            if(StringUtils.hasText(accid) && StringUtils.hasText(accEmail))
                                            {
                                                accEmails.put(accid,accEmail);
                                            }

                                        }


                                    }

                                }

                            }

                            //Filter by Email
                           Optional<Map.Entry<String,String>> OptionalAcc =  accEmails.entrySet().stream().filter(u->u.getValue().equals(userEmail)).findFirst();
                           if(OptionalAcc.isPresent())
                           {
                                Map.Entry<String,String> account = OptionalAcc.get();
                                accountID = account.getKey(); //Return Account ID
                           }
                           



                        }
                    }
                } catch (IOException e)
                {
                    throw new EX_ESMAPI(msgSrc.getMessage("API_AC_ERROR", new Object[] { e.getLocalizedMessage() },
                            Locale.ENGLISH));
                }
            }

        }
        return accountID;
    }


    @Override
    public TY_UserESS getESSDetails(@AuthenticationPrincipal Token token) throws EX_ESMAPI 
    {
        TY_UserESS userDetails = new TY_UserESS();

        //1. Get User's Details
        userDetails.setUserDetails(this.getUserDetails(token));

        //2.a. Account Identified - Show tickets
        if(userDetails.getUserDetails() != null)
        {
            if(StringUtils.hasText(userDetails.getUserDetails().getAccountId()) || StringUtils.hasText(userDetails.getUserDetails().getContactId()) )
            {
                //Get All Cases for the User 
                    // Account ID as Account
                          //OR
                    // Contact ID as reporter
                    
                try 
                {
                    userDetails.setCases(getCases4User());
                } 
                catch (IOException e)
                {
                    throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASES_USER", new Object[]{userData.getUserId(), e.getLocalizedMessage()}, Locale.ENGLISH));
                }    
                    

            }
            else
            {
               //2.b. Account Not Identified - Create Account and Update Session 
               this.userData.setAccountId(createAccount());
            }
        }

        

        return userDetails;
    }


    @Override
    public String createAccount() throws EX_ESMAPI
    {
        String accountId= null;
        //User Email and UserName Bound
        if(StringUtils.hasText(userData.getUserEmail()) && StringUtils.hasText(userData.getUserName()) )
        {
            TY_AccountCreate newAccount = new TY_AccountCreate
            (userData.getUserName(), GC_Constants.gc_roleCustomer, GC_Constants.gc_statusACTIVE, new TY_DefaultComm(userData.getUserEmail()) );

            if(newAccount != null)
            {
                HttpClient httpclient = HttpClients.createDefault();
                String accPOSTURL = getAccountsPOSTURL();
                if(StringUtils.hasText(accPOSTURL))
                {
                    String encoding = Base64.getEncoder().encodeToString((srvCloudUrls.getUserName() + ":" + srvCloudUrls.getPassword()).getBytes());
                    HttpPost httpPost = new HttpPost(accPOSTURL);
                    httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
                    httpPost.addHeader("Content-Type", "application/json");

                    ObjectMapper objMapper = new ObjectMapper();
                    try 
                    {
                        String requestBody = objMapper.writeValueAsString(newAccount);
                        System.out.println(requestBody);

                        StringEntity entity = new StringEntity(requestBody,ContentType.APPLICATION_JSON);
                        httpPost.setEntity(entity);

                        //POST Account in Service Cloud
                        try 
                        {
                            // Fire the Url
                            HttpResponse response = httpclient.execute(httpPost);
                            // verify the valid error code first
                            int statusCode = response.getStatusLine().getStatusCode();
                            if (statusCode != HttpStatus.SC_CREATED) 
                            {
                                throw new RuntimeException("Failed with HTTP error code : " + statusCode);
                            }

                            // Try and Get Entity from Response
                            HttpEntity entityResp = response.getEntity();
                            String apiOutput = EntityUtils.toString(entityResp);
                           
                            // Conerting to JSON
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode jsonNode = mapper.readTree(apiOutput);

                            if(jsonNode != null)
                            {
                
                                JsonNode rootNode = jsonNode.path("value");
                                if(rootNode != null)
                                {
                                
                                    System.out.println("Account Bound!!");
                                    
                    
                                    Iterator<Map.Entry<String, JsonNode>> payloadItr = jsonNode.fields();
                                    while (payloadItr.hasNext()) 
                                    {
                                        System.out.println("Payload Iterator Bound");
                                        Map.Entry<String, JsonNode> payloadEnt = payloadItr.next();
                                        String   payloadFieldName  = payloadEnt.getKey();
                                        System.out.println("Payload Field Scanned:  " + payloadFieldName);
                    
                                        if(payloadFieldName.equals("value"))
                                        {
                                            JsonNode accEnt = payloadEnt.getValue();
                                            System.out.println("New Account Entity Bound");
                                            if(accEnt != null)
                                                {
                                                    
                                                    System.out.println("Accounts Entity Bound - Reading Account...");
                                                    Iterator<String> fieldNames = accEnt.fieldNames();
                                                    while (fieldNames.hasNext()) 
                                                    {
                                                        String   accFieldName  = fieldNames.next();
                                                        System.out.println("Account Entity Field Scanned:  " + accFieldName);
                                                        if(accFieldName.equals("id"))
                                                        {
                                                            System.out.println("Account GUID Added : " + accEnt.get(accFieldName).asText());
                                                            if(StringUtils.hasText(accEnt.get(accFieldName).asText()))
                                                            {
                                                                accountId = accEnt.get(accFieldName).asText();
                                                            }
                                                        }
                                                        
                                                    }
                                                    
                                                }
                                                
                                            
                
                                        }							
                
                                    }			
                                }	
                            }		
                




                        } 
                        catch (IOException e)
                        {
                            throw new EX_ESMAPI(msgSrc.getMessage("ERR_ACC_POST", new Object[] { e.getLocalizedMessage() },
                                 Locale.ENGLISH));
                        }
                    }
                    catch (JsonProcessingException e) 
                    {
                        throw new EX_ESMAPI(msgSrc.getMessage("ERR_NEW_AC_JSON", new Object[] { e.getLocalizedMessage() },
                        Locale.ENGLISH));
                    }
                    
                  


                }

                


            }
        }
         return accountId;
    }
   

    
    private String getAccountsPOSTURL()
    {
        String url = null;
        if(StringUtils.hasText(srvCloudUrls.getAccountsUrl()))
        {
            String[] urlParts = srvCloudUrls.getAccountsUrl().split("\\?");
            if(urlParts.length > 0)
            {
                url = urlParts[0];
            }
        }
        return url;
    }

    private JsonNode getAllAccounts() throws IOException
    {
        JsonNode jsonNode = null;
        HttpResponse response = null;
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        String url = null;

        try 
        {
            if (StringUtils.hasLength(srvCloudUrls.getUserName()) && StringUtils.hasLength(srvCloudUrls.getPassword()) && StringUtils.hasLength(srvCloudUrls.getAccountsUrl())) 
            {
                System.out.println("Url and Credentials Found!!");

                long numAccounts = apiSrv.getNumberofEntitiesByUrl(srvCloudUrls.getAccountsUrl());
                if(numAccounts > 0)
                {
                    url = srvCloudUrls.getAccountsUrl() + srvCloudUrls.getTopSuffix() + GC_Constants.equalsString + numAccounts;
                    String encoding = Base64.getEncoder().encodeToString((srvCloudUrls.getUserName() + ":" + srvCloudUrls.getPassword()).getBytes());

                    HttpGet httpGet = new HttpGet(url);
                    httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
                    httpGet.addHeader("accept", "application/json");
    
                    try 
                    {
                        //Fire the Url
                        response = httpClient.execute(httpGet);
    
                        // verify the valid error code first
                        int statusCode = response.getStatusLine().getStatusCode();
                        if (statusCode != HttpStatus.SC_OK) 
                        {
                            throw new RuntimeException("Failed with HTTP error code : " + statusCode);
                        }
    
                        //Try and Get Entity from Response
                        org.apache.http.HttpEntity entity = response.getEntity();
                        String apiOutput = EntityUtils.toString(entity);
                        //Lets see what we got from API
                        System.out.println(apiOutput);
    
                        //Conerting to JSON
                        ObjectMapper mapper = new ObjectMapper();
                        jsonNode = mapper.readTree(apiOutput);
                        
    
                    } catch (IOException e)
                    {
    
                        e.printStackTrace();
                    }
                }

               

            }

        } 
        finally
        {
            httpClient.close();
        }
        return jsonNode;

        

    }


    private JsonNode getAllContacts() throws IOException
    {
        JsonNode jsonNode = null;
        HttpResponse response = null;
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        String url = null;

        try 
        {
            if (StringUtils.hasLength(srvCloudUrls.getUserName()) && StringUtils.hasLength(srvCloudUrls.getPassword()) && StringUtils.hasLength(srvCloudUrls.getCpUrl())) 
            {
                System.out.println("Url and Credentials Found!!");

                long numAccounts = apiSrv.getNumberofEntitiesByUrl(srvCloudUrls.getCpUrl());
                if(numAccounts > 0)
                {
                    url = srvCloudUrls.getCpUrl() + srvCloudUrls.getTopSuffix() + GC_Constants.equalsString + numAccounts;
                    String encoding = Base64.getEncoder().encodeToString((srvCloudUrls.getUserName() + ":" + srvCloudUrls.getPassword()).getBytes());

                    HttpGet httpGet = new HttpGet(url);
                    httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
                    httpGet.addHeader("accept", "application/json");
    
                    try 
                    {
                        //Fire the Url
                        response = httpClient.execute(httpGet);
    
                        // verify the valid error code first
                        int statusCode = response.getStatusLine().getStatusCode();
                        if (statusCode != HttpStatus.SC_OK) 
                        {
                            throw new RuntimeException("Failed with HTTP error code : " + statusCode);
                        }
    
                        //Try and Get Entity from Response
                        org.apache.http.HttpEntity entity = response.getEntity();
                        String apiOutput = EntityUtils.toString(entity);
                        //Lets see what we got from API
                        System.out.println(apiOutput);
    
                        //Conerting to JSON
                        ObjectMapper mapper = new ObjectMapper();
                        jsonNode = mapper.readTree(apiOutput);
                        
    
                    } catch (IOException e)
                    {
    
                        e.printStackTrace();
                    }
                }

               

            }

        } 
        finally
        {
            httpClient.close();
        }
        return jsonNode;

        

    }


    private List<TY_CaseESS> getCases4User()throws IOException
    {
        List<TY_CaseESS> casesESSList = null;

        List<TY_CaseESS> casesESSList4User = null;

        try
        {
            
            JsonNode jsonNode = getAllCases();

            if(jsonNode != null)
            {

                JsonNode rootNode = jsonNode.path("value");
                if(rootNode != null)
                {
                    System.out.println("Cases Bound!!");
                    casesESSList = new ArrayList<TY_CaseESS>();
    
                    Iterator<Map.Entry<String, JsonNode>> payloadItr = jsonNode.fields();
                    while (payloadItr.hasNext()) 
                    {
                        System.out.println("Payload Iterator Bound");
                        Map.Entry<String, JsonNode> payloadEnt = payloadItr.next();
                        String   payloadFieldName  = payloadEnt.getKey();
                        System.out.println("Payload Field Scanned:  " + payloadFieldName);
    
                        if(payloadFieldName.equals("value"))
                        {
                            Iterator<JsonNode> casesItr = payloadEnt.getValue().elements();
                            System.out.println("Cases Iterator Bound");
                            while (casesItr.hasNext()) 
                            {
                                
                                JsonNode caseEnt = casesItr.next();
                                if(caseEnt != null)
                                {
                                    String caseid = null, caseguid = null, subject= null, status= null,createdOn=null, accountId= null, contactId= null ;
                                    System.out.println("Cases Entity Bound - Reading Case...");
                                    Iterator<String> fieldNames = caseEnt.fieldNames();
                                    while (fieldNames.hasNext()) 
                                    {
                                        String   caseFieldName  = fieldNames.next();
                                        System.out.println("Case Entity Field Scanned:  " + caseFieldName);
                                        if(caseFieldName.equals("id"))
                                        {
                                            System.out.println("Case GUID Added : " + caseEnt.get(caseFieldName).asText());
                                            if(StringUtils.hasText(caseEnt.get(caseFieldName).asText()))
                                            {
                                                caseguid = caseEnt.get(caseFieldName).asText();
                                            }
                                        }

                                        if(caseFieldName.equals("displayId"))
                                        {
                                            System.out.println("Case Id Added : " + caseEnt.get(caseFieldName).asText());
                                            if(StringUtils.hasText(caseEnt.get(caseFieldName).asText()))
                                            {
                                                caseid = caseEnt.get(caseFieldName).asText();
                                            }
                                        }

                                        if(caseFieldName.equals("subject"))
                                        {
                                            System.out.println("Case Subject Added : " + caseEnt.get(caseFieldName).asText());
                                            if(StringUtils.hasText(caseEnt.get(caseFieldName).asText()))
                                            {
                                                subject = caseEnt.get(caseFieldName).asText();
                                            }
                                        }

                                        if(caseFieldName.equals("statusDescription"))
                                        {
                                            System.out.println("Case Status Added : " + caseEnt.get(caseFieldName).asText());
                                            if(StringUtils.hasText(caseEnt.get(caseFieldName).asText()))
                                            {
                                                status = caseEnt.get(caseFieldName).asText();
                                            }
                                        }

                                        if(caseFieldName.equals("statusDescription"))
                                        {
                                            System.out.println("Case Status Added : " + caseEnt.get(caseFieldName).asText());
                                            if(StringUtils.hasText(caseEnt.get(caseFieldName).asText()))
                                            {
                                                status = caseEnt.get(caseFieldName).asText();
                                            }
                                        }

                                        if (caseFieldName.equals("adminData")) 
                                                {
                                                    System.out.println("Inside Admin Data:  " );

                                                    JsonNode admEnt = caseEnt.path("adminData");
                                                    if(admEnt != null)
                                                    {
                                                        System.out.println("AdminData Node Bound");

                                                        Iterator<String> fieldNamesAdm= admEnt.fieldNames();
                                                        while (fieldNamesAdm.hasNext()) 
                                                        {
                                                            String admFieldName = fieldNamesAdm.next();
                                                            if (admFieldName.equals("createdOn")) 
                                                                {
                                                                    System.out.println(
                                                                            "Created On : " + admEnt.get(admFieldName).asText());
                                                                    createdOn = admEnt.get(admFieldName).asText();
                                                                }
                                                        }

                                                    }
                                                }

                                        if (caseFieldName.equals("account")) 
                                                {
                                                    System.out.println("Inside Account:  " );

                                                    JsonNode accEnt = caseEnt.path("account");
                                                    if(accEnt != null)
                                                    {
                                                        System.out.println("Account Node Bound");

                                                        Iterator<String> fieldNamesAcc= accEnt.fieldNames();
                                                        while (fieldNamesAcc.hasNext()) 
                                                        {
                                                            String accFieldName = fieldNamesAcc.next();
                                                            if (accFieldName.equals("id")) 
                                                                {
                                                                    System.out.println(
                                                                            "Account ID : " + accEnt.get(accFieldName).asText());
                                                                    accountId = accEnt.get(accFieldName).asText();
                                                                }
                                                        }

                                                    }
                                                }        

                                        if (caseFieldName.equals("reporter")) 
                                                {
                                                    System.out.println("Inside Reporter:  " );

                                                    JsonNode repEnt = caseEnt.path("reporter");
                                                    if(repEnt != null)
                                                    {
                                                        System.out.println("Reporter Node Bound");

                                                        Iterator<String> fieldNamesRep= repEnt.fieldNames();
                                                        while (fieldNamesRep.hasNext()) 
                                                        {
                                                            String repFieldName = fieldNamesRep.next();
                                                            if (repFieldName.equals("id")) 
                                                                {
                                                                    System.out.println(
                                                                            "Reporter ID : " + repEnt.get(repFieldName).asText());
                                                                    contactId = repEnt.get(repFieldName).asText();
                                                                }
                                                        }

                                                    }
                                                }                

                                                                    
                                    }

                                    if(StringUtils.hasText(caseid) && StringUtils.hasText(caseguid))
                                    {
                                        if(StringUtils.hasText(createdOn))
                                        {
                                            // Parse the date-time string into OffsetDateTime
                                            OffsetDateTime odt = OffsetDateTime.parse(createdOn);
                                            // Convert OffsetDateTime into Instant
                                            Instant instant = odt.toInstant();
                                             // If at all, you need java.util.Date
                                            Date date = Date.from(instant);

                                            SimpleDateFormat sdf = new SimpleDateFormat("dd/M/yyyy");
                                            String dateFormatted= sdf.format(date);
                                            
                                            casesESSList.add(new TY_CaseESS(caseguid, caseid, subject, status, accountId, contactId, createdOn, date, dateFormatted));

                                        }
                                        else
                                        {
                                            casesESSList.add(new TY_CaseESS(caseguid, caseid, subject, status, accountId, contactId, createdOn, null,null));
                                        }
                                        
                                    }
    
                                }
                           
    
                            }
    
                        }
                                 
                    }
                }
                   
            }

        }

       catch (Exception e) 
        {
            e.printStackTrace();
        }
        

       /*
         ------- FILTER FOR USER ACCOUNT or REPORTED BY CONTACT PERSON
       */

       if(! CollectionUtils.isEmpty(casesESSList))
       {
            casesESSList4User = casesESSList.stream().filter 
            (
                e->
                {

                    if(StringUtils.hasText(e.getContactId()))
                    {

                        if( e.getAccountId().equals(userData.getAccountId()) 
                            ||
                            e.getContactId().equals(userData.getContactId()) )
                            {
                                return true;
                            }
                        
                    }
                    else
                    {
                        if( e.getAccountId().equals(userData.getAccountId()) ) 
                        {
                            return true;
                        }

                    }
                    return false; 
                  
                       
    
                }
            ).collect(Collectors.toList());
       }
   


               


        return casesESSList4User;
    }

    

    private JsonNode getAllCases() throws IOException
    {
        JsonNode jsonNode = null;
        HttpResponse response = null;
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        String url = null;

        try 
        {
            if (StringUtils.hasLength(srvCloudUrls.getUserName()) && StringUtils.hasLength(srvCloudUrls.getPassword()) && StringUtils.hasLength(srvCloudUrls.getCasesUrl())) 
            {
                System.out.println("Url and Credentials Found!!");

                long numCases = apiSrv.getNumberofEntitiesByUrl(srvCloudUrls.getCasesUrl());
                if (numCases > 0)
                {
                    url = srvCloudUrls.getCasesUrl() + srvCloudUrls.getTopSuffix() + GC_Constants.equalsString + numCases;

                    String encoding = Base64.getEncoder()
                            .encodeToString((srvCloudUrls.getUserName() + ":" + srvCloudUrls.getPassword()).getBytes());

                    HttpGet httpGet = new HttpGet(url);
                    httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
                    httpGet.addHeader("accept", "application/json");

                    try 
                    {
                        // Fire the Url
                        response = httpClient.execute(httpGet);

                        // verify the valid error code first
                        int statusCode = response.getStatusLine().getStatusCode();
                        if (statusCode != HttpStatus.SC_OK) 
                        {
                            throw new RuntimeException("Failed with HTTP error code : " + statusCode);
                        }

                        // Try and Get Entity from Response
                        HttpEntity entity = response.getEntity();
                        String apiOutput = EntityUtils.toString(entity);
                        // Lets see what we got from API
                        System.out.println(apiOutput);

                        // Conerting to JSON
                        ObjectMapper mapper = new ObjectMapper();
                        jsonNode = mapper.readTree(apiOutput);

                    } 
                    catch (IOException e) 
                    {

                        e.printStackTrace();
                    }

                }

            }

        } 
        finally
        {
            httpClient.close();
        }
        return jsonNode;

        

    }

    




}
