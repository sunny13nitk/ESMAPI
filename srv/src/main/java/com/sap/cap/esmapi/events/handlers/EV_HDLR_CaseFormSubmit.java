package com.sap.cap.esmapi.events.handlers;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sap.cap.esmapi.catg.pojos.TY_CatgCus;
import com.sap.cap.esmapi.catg.pojos.TY_CatgCusItem;
import com.sap.cap.esmapi.catg.srv.intf.IF_CatalogSrv;
import com.sap.cap.esmapi.events.event.EV_CaseFormSubmit;
import com.sap.cap.esmapi.events.event.EV_LogMessage;
import com.sap.cap.esmapi.exceptions.EX_ESMAPI;
import com.sap.cap.esmapi.ui.pojos.TY_Attachment;
import com.sap.cap.esmapi.utilities.constants.GC_Constants;
import com.sap.cap.esmapi.utilities.enums.EnumMessageType;
import com.sap.cap.esmapi.utilities.enums.EnumStatus;
import com.sap.cap.esmapi.utilities.pojos.TY_Account_CaseCreate;
import com.sap.cap.esmapi.utilities.pojos.TY_AttachmentResponse;
import com.sap.cap.esmapi.utilities.pojos.TY_Attachment_CaseCreate;
import com.sap.cap.esmapi.utilities.pojos.TY_Case_SrvCloud;
import com.sap.cap.esmapi.utilities.pojos.TY_CatgLvl1_CaseCreate;
import com.sap.cap.esmapi.utilities.pojos.TY_Description_CaseCreate;
import com.sap.cap.esmapi.utilities.pojos.TY_Message;
import com.sap.cap.esmapi.utilities.pojos.TY_NotesCreate;
import com.sap.cap.esmapi.utilities.srv.intf.IF_UserSessionSrv;
import com.sap.cap.esmapi.utilities.srvCloudApi.srv.intf.IF_SrvCloudAPI;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class EV_HDLR_CaseFormSubmit
{
    @Autowired
    private TY_CatgCus catgCusSrv;

    @Autowired
    private IF_CatalogSrv catalogTreeSrv;

    @Autowired
    private IF_UserSessionSrv userSessSrv;

    @Autowired
    private MessageSource msgSrc;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private IF_SrvCloudAPI srvCloudApiSrv;

    @Async
    @EventListener
    public void handleCaseFormSubmission(EV_CaseFormSubmit evCaseFormSubmit)
    {
        if (evCaseFormSubmit != null && userSessSrv != null)
        {
            log.info("Inside Case Form Submit Event Processing----");

            if (evCaseFormSubmit.getPayload() != null)
            {
                log.info("Case Payload Bound...");
                if (evCaseFormSubmit.getPayload().isValid())
                {
                    log.info("Case Payload is found to be successfully validated .......");

                    // Submission Id found on Case
                    if (StringUtils.hasText(evCaseFormSubmit.getPayload().getSubmGuid()))
                    {
                        TY_Case_SrvCloud newCaseEntity = new TY_Case_SrvCloud();
                        Optional<TY_CatgCusItem> cusItemO = null;
                        TY_AttachmentResponse attR = null;

                        cusItemO = catgCusSrv.getCustomizations().stream()
                                .filter(g -> g.getCaseType()
                                        .equals(evCaseFormSubmit.getPayload().getCaseForm().getCaseTxnType()))
                                .findFirst();

                        if (userSessSrv.getUserDetails4mSession() != null)
                        {
                            // If An Employee has not logged in
                            if (!userSessSrv.getUserDetails4mSession().isEmployee())
                            {
                                // Account must be present
                                if (StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId()))
                                {
                                    newCaseEntity.setAccount(new TY_Account_CaseCreate(
                                            userSessSrv.getUserDetails4mSession().getAccountId())); // Account ID
                                }
                            }
                            else
                            {
                                // Push Employee ID from Session Service once #ESM module is enabled
                            }

                            newCaseEntity.setCaseType(evCaseFormSubmit.getPayload().getCaseForm().getCaseTxnType()); // Case
                                                                                                                     // Txn.
                                                                                                                     // Type
                            newCaseEntity.setSubject(evCaseFormSubmit.getPayload().getCaseForm().getSubject()); // Subject

                            // Fetch CatgGuid by description from Customizing - Set Categories
                            if (cusItemO.isPresent() && catalogTreeSrv != null)
                            {
                                String[] catTreeSelCatg = catalogTreeSrv.getCatgHierarchyforCatId(
                                        evCaseFormSubmit.getPayload().getCaseForm().getCatgDesc(),
                                        cusItemO.get().getCaseTypeEnum());
                                if (Arrays.stream(catTreeSelCatg).filter(e -> e != null).count() > 0)
                                {
                                    switch ((int) Arrays.stream(catTreeSelCatg).filter(e -> e != null).count())
                                    {
                                    case 4:
                                        newCaseEntity.setCategoryLevel1(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[3]));
                                        newCaseEntity.setCategoryLevel2(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[2]));
                                        newCaseEntity.setCategoryLevel3(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[1]));
                                        newCaseEntity.setCategoryLevel4(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[0]));
                                        break;
                                    case 3:
                                        newCaseEntity.setCategoryLevel1(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[2]));
                                        newCaseEntity.setCategoryLevel2(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[1]));
                                        newCaseEntity.setCategoryLevel3(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[0]));
                                        break;
                                    case 2:
                                        newCaseEntity.setCategoryLevel1(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[1]));
                                        newCaseEntity.setCategoryLevel2(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[0]));
                                        break;
                                    case 1:
                                        newCaseEntity.setCategoryLevel1(new TY_CatgLvl1_CaseCreate(catTreeSelCatg[0]));
                                    default:

                                        handleCatgError(evCaseFormSubmit, cusItemO);

                                    }
                                }
                                else
                                {

                                    handleCatgError(evCaseFormSubmit, cusItemO);

                                }

                                // Create Notes if There is a description
                                if (StringUtils.hasText(evCaseFormSubmit.getPayload().getCaseForm().getDescription()))
                                {
                                    // Create Note and Get Guid back
                                    String noteId = srvCloudApiSrv.createNotes(new TY_NotesCreate(
                                            evCaseFormSubmit.getPayload().getCaseForm().getDescription()));
                                    if (StringUtils.hasText(noteId))
                                    {
                                        newCaseEntity.setDescription(new TY_Description_CaseCreate(noteId));
                                    }
                                }

                                // Check if Attachment needs to be Created
                                if (evCaseFormSubmit.getPayload().getCaseForm().getAttachment() != null)
                                {
                                    try
                                    {
                                        if (evCaseFormSubmit.getPayload().getCaseForm().getAttachment()
                                                .getBytes() != null)
                                        {
                                            // Create Attachment
                                            TY_Attachment newAttachment = new TY_Attachment(
                                                    FilenameUtils.getName(evCaseFormSubmit.getPayload().getCaseForm()
                                                            .getAttachment().getOriginalFilename()),
                                                    GC_Constants.gc_Attachment_Category, false);
                                            attR = srvCloudApiSrv.createAttachment(newAttachment);
                                            if (attR != null)
                                            {
                                                if (StringUtils.hasText(attR.getId())
                                                        && StringUtils.hasText(attR.getUploadUrl()))
                                                {
                                                    log.info("Attachment id :" + attR.getId()
                                                            + " generated for Upload Url : " + attR.getUploadUrl());

                                                    if (srvCloudApiSrv.persistAttachment(attR.getUploadUrl(),
                                                            evCaseFormSubmit.getPayload().getCaseForm()
                                                                    .getAttachment()))
                                                    {
                                                        log.info("Attachment with id : " + attR.getId()
                                                                + " Persisted in Document Container..");

                                                        // Prepare POJOdetails for TY_Case_SrvCloud newCaseEntity
                                                        List<TY_Attachment_CaseCreate> caseAttachmentsNew = new ArrayList<TY_Attachment_CaseCreate>();
                                                        TY_Attachment_CaseCreate caseAttachment = new TY_Attachment_CaseCreate(
                                                                attR.getId());
                                                        caseAttachmentsNew.add(caseAttachment);
                                                        newCaseEntity.setAttachments(caseAttachmentsNew);

                                                    }

                                                }

                                            }
                                        }
                                    }
                                    catch (EX_ESMAPI | IOException e)
                                    {

                                        if (e instanceof IOException)
                                        {
                                            handleNoFileDataAttachment(evCaseFormSubmit);
                                        }
                                        else if (e instanceof EX_ESMAPI)
                                        {
                                            handleAttachmentPersistError(evCaseFormSubmit, attR, e);
                                        }
                                    }
                                }

                                // Case Payload is now Ready: Post and get the Case ID back
                                try
                                {
                                    String caseID = srvCloudApiSrv.createCase(newCaseEntity);
                                    if (StringUtils.hasText(caseID))
                                    {
                                        handleCaseSuccCreated(evCaseFormSubmit, cusItemO, caseID);

                                    }
                                }
                                catch (Exception e)
                                {

                                    handleCaseCreationError(evCaseFormSubmit, e);

                                }

                            }

                        }
                    }
                }
            }
        }
    }

    private void handleCaseCreationError(EV_CaseFormSubmit evCaseFormSubmit, Exception e)
    {
        String msg;
        msg = msgSrc.getMessage("ERR_CASE_POST", new Object[]
        { e.getLocalizedMessage(), evCaseFormSubmit.getPayload().getSubmGuid() },
                Locale.ENGLISH);

        log.error(msg);
        TY_Message logMsg = new TY_Message(evCaseFormSubmit.getPayload().getUserId(),
                Timestamp.from(Instant.now()), EnumStatus.Error,
                EnumMessageType.ERR_CASE_CREATE, evCaseFormSubmit.getPayload().getSubmGuid(),
                msg);
        userSessSrv.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage(
                (Object) evCaseFormSubmit.getPayload().getSubmGuid(), logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);

        // Should be handled Centrally via Aspect
        throw new EX_ESMAPI(msg);
    }

    private void handleCaseSuccCreated(EV_CaseFormSubmit evCaseFormSubmit, Optional<TY_CatgCusItem> cusItemO,
            String caseID)
    {
        String msg = "Case ID : " + caseID + " created..";
        log.info(msg);
        msg = msgSrc.getMessage("SUCC_CASE", new Object[]
        { caseID, cusItemO.get().getCaseTypeEnum().toString(), evCaseFormSubmit.getPayload().getSubmGuid() },
                Locale.ENGLISH);
        // Populate Success message in session
        userSessSrv.addSessionMessage(msg);

        TY_Message logMsg = new TY_Message(evCaseFormSubmit.getPayload().getUserId(), Timestamp.from(Instant.now()),
                EnumStatus.Success, EnumMessageType.SUCC_CASE_CREATE, evCaseFormSubmit.getPayload().getSubmGuid(), msg);
        userSessSrv.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) evCaseFormSubmit.getPayload().getSubmGuid(), logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);
    }

    private void handleAttachmentPersistError(EV_CaseFormSubmit evCaseFormSubmit, TY_AttachmentResponse attR,
            Exception e)
    {
        String msg;
        msg = msgSrc.getMessage("ERROR_DOCS_PERSIST", new Object[]
        { attR.getUploadUrl(), evCaseFormSubmit.getPayload().getCaseForm().getAttachment().getOriginalFilename(),
                e.getLocalizedMessage() }, Locale.ENGLISH);

        log.error(msg);
        TY_Message logMsg = new TY_Message(evCaseFormSubmit.getPayload().getUserId(), Timestamp.from(Instant.now()),
                EnumStatus.Error, EnumMessageType.ERR_ATTACHMENT, evCaseFormSubmit.getPayload().getSubmGuid(), msg);
        userSessSrv.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) evCaseFormSubmit.getPayload().getSubmGuid(), logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);
        // Should be handled Centrally via Aspect
        throw new EX_ESMAPI(msg);
    }

    private void handleNoFileDataAttachment(EV_CaseFormSubmit evCaseFormSubmit)
    {
        String msg;
        msg = msgSrc.getMessage("FILE_NO_DATA", new Object[]
        { evCaseFormSubmit.getPayload().getCaseForm().getAttachment().getOriginalFilename() }, Locale.ENGLISH);

        log.error(msg);
        TY_Message logMsg = new TY_Message(evCaseFormSubmit.getPayload().getUserId(), Timestamp.from(Instant.now()),
                EnumStatus.Error, EnumMessageType.ERR_ATTACHMENT, evCaseFormSubmit.getPayload().getSubmGuid(), msg);
        userSessSrv.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) evCaseFormSubmit.getPayload().getSubmGuid(), logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);

        // Should be handled Centrally via Aspect
        throw new EX_ESMAPI(msg);
    }

    private void handleCatgError(EV_CaseFormSubmit evCaseFormSubmit, Optional<TY_CatgCusItem> cusItemO)
    {
        String msg = msgSrc.getMessage("ERR_INVALID_CATG", new Object[]
        { cusItemO.get().getCaseTypeEnum().toString(), evCaseFormSubmit.getPayload().getCaseForm().getCatgDesc() },
                Locale.ENGLISH);

        log.error(msg);
        TY_Message logMsg = new TY_Message(evCaseFormSubmit.getPayload().getUserId(), Timestamp.from(Instant.now()),
                EnumStatus.Error, EnumMessageType.ERR_CASE_CATG, evCaseFormSubmit.getPayload().getSubmGuid(), msg);
        userSessSrv.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) evCaseFormSubmit.getPayload().getSubmGuid(), logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);
    }
}
