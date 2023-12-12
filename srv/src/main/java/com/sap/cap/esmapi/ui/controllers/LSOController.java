package com.sap.cap.esmapi.ui.controllers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sap.cap.esmapi.catg.pojos.TY_CatgCus;
import com.sap.cap.esmapi.catg.pojos.TY_CatgCusItem;
import com.sap.cap.esmapi.catg.pojos.TY_CatgTemplates;
import com.sap.cap.esmapi.catg.srv.intf.IF_CatalogSrv;
import com.sap.cap.esmapi.catg.srv.intf.IF_CatgSrv;
import com.sap.cap.esmapi.events.event.EV_CaseFormSubmit;
import com.sap.cap.esmapi.events.event.EV_CaseReplySubmit;
import com.sap.cap.esmapi.exceptions.EX_ESMAPI;
import com.sap.cap.esmapi.ui.pojos.TY_CaseEditFormAsync;
import com.sap.cap.esmapi.ui.pojos.TY_CaseEdit_Form;
import com.sap.cap.esmapi.ui.pojos.TY_CaseFormAsync;
import com.sap.cap.esmapi.ui.pojos.TY_Case_Form;
import com.sap.cap.esmapi.utilities.enums.EnumCaseTypes;
import com.sap.cap.esmapi.utilities.enums.EnumMessageType;
import com.sap.cap.esmapi.utilities.pojos.TY_Message;
import com.sap.cap.esmapi.utilities.pojos.TY_RLConfig;
import com.sap.cap.esmapi.utilities.pojos.TY_UserESS;
import com.sap.cap.esmapi.utilities.srv.intf.IF_SessAttachmentsService;
import com.sap.cap.esmapi.utilities.srv.intf.IF_UserSessionSrv;
import com.sap.cap.esmapi.vhelps.srv.intf.IF_VHelpLOBUIModelSrv;
import com.sap.cds.services.request.UserInfo;
import com.sap.cloud.security.xsuaa.token.Token;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/lso")
public class LSOController
{
    @Autowired
    private IF_UserSessionSrv userSessSrv;

    @Autowired
    private UserInfo userInfo;

    @Autowired
    private TY_CatgCus catgCusSrv;

    @Autowired
    private IF_CatgSrv catgTreeSrv;

    @Autowired
    private IF_CatalogSrv catalogTreeSrv;

    @Autowired
    private MessageSource msgSrc;

    @Autowired
    private IF_VHelpLOBUIModelSrv vhlpUISrv;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private IF_SessAttachmentsService attSrv;

    @Autowired
    private TY_RLConfig rlConfig;

    private final String caseListVWRedirect = "redirect:/lso/";
    private final String caseFormErrorRedirect = "redirect:/lso/errForm/";
    private final String caseFormView = "caseFormLSO";
    private final String caseFormReply = "caseFormReplyLSO";
    private final String lsoCaseListView = "lsoCasesListViewLX";
    private final String caseFormReplyErrorRedirect = "redirect:/lso/errCaseReply/";

    @GetMapping("/")
    public String showCasesList(@AuthenticationPrincipal Token token, Model model)
    {

        if (token != null && userInfo != null && userSessSrv != null)
        {
            // Only Authenticated user via IDP
            if (userInfo.isAuthenticated())
            {

                // #AUTH checks to be done later after role collection(s) are published in
                // CL_UserSessionSrv
                TY_UserESS userDetails = new TY_UserESS();

                if (userSessSrv != null)
                {
                    // Get User Info. from XSUAA TOken
                    if (userSessSrv.getESSDetails(token, true) != null)
                        // check User and Account Bound
                        if (userSessSrv.getUserDetails4mSession() != null)
                        {
                            log.info("User Details Bound from Session!");
                            if (StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                                    || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                            {
                                if (!CollectionUtils.isEmpty(catgCusSrv.getCustomizations()))
                                {

                                    Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                                            .filter(g -> g.getCaseTypeEnum().toString()
                                                    .equals(EnumCaseTypes.Learning.toString()))
                                            .findFirst();
                                    if (cusItemO.isPresent() && catgTreeSrv != null)
                                    {
                                        userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                                        log.info("Fetching Cases for User From Session : "
                                                + userSessSrv.getUserDetails4mSession().toString());
                                        userDetails.setCases(userSessSrv.getCases4User4mSession());
                                        model.addAttribute("userInfo", userDetails);
                                        model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());
                                        // Rate Limit Simulation
                                        model.addAttribute("rateLimitBreached",
                                                userSessSrv.getCurrentRateLimitBreachedValue());

                                        // Even if No Cases - spl. for Newly Create Acc - to enable REfresh button
                                        model.addAttribute("sessMsgs", userSessSrv.getSessionMessages());

                                    }

                                    else
                                    {

                                        throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                                        { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
                                    }
                                }

                            }
                        }

                }
            }

        }
        log.info("View name for LSO List view: " + lsoCaseListView);
        return lsoCaseListView;

    }

    @GetMapping("/createCase/")
    public String showCaseAsyncForm(Model model)
    {
        String viewCaseForm = caseFormView;

        if ((StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations()) && userInfo.isAuthenticated())
        {

            Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                    .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString())).findFirst();
            if (cusItemO.isPresent() && catgTreeSrv != null)
            {

                model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                // Before case form Inititation we must check the Rate Limit for the Current
                // User Session --current Form Submission added for Rate Limit Evaulation
                if (userSessSrv.isWithinRateLimit())
                {
                    // Populate User Details
                    TY_UserESS userDetails = new TY_UserESS();
                    userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                    model.addAttribute("userInfo", userDetails);

                    // Initialize Attachments Session Service
                    if (attSrv != null)
                    {
                        attSrv.initialize();
                    }
                    // Populate Case Form Details
                    TY_Case_Form caseForm = new TY_Case_Form();
                    if (userSessSrv.getUserDetails4mSession().isEmployee())
                    {
                        caseForm.setAccId(userSessSrv.getUserDetails4mSession().getEmployeeId()); // hidden
                    }
                    else
                    {
                        caseForm.setAccId(userSessSrv.getUserDetails4mSession().getAccountId()); // hidden
                    }

                    caseForm.setCaseTxnType(cusItemO.get().getCaseType()); // hidden
                    model.addAttribute("caseForm", caseForm);

                    model.addAttribute("formErrors", null);

                    // clear Form errors on each refresh or a New Case form request
                    if (CollectionUtils.isNotEmpty(userSessSrv.getFormErrors()))
                    {
                        userSessSrv.clearFormErrors();

                    }

                    // also Upload the Catg. Tree as per Case Type
                    model.addAttribute("catgsList",
                            catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

                    // Attachment file Size
                    model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());

                }
                else
                {
                    // Not Within Rate Limit - REdirect to List View
                    viewCaseForm = caseListVWRedirect;

                }

            }
            else
            {

                throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
            }

        }

        return viewCaseForm;
    }

    @PostMapping(value = "/saveCase", params = "action=saveCase")
    public String saveCase(@ModelAttribute("caseForm") TY_Case_Form caseForm, Model model)
    {

        String viewName = caseListVWRedirect;
        if (caseForm != null && userSessSrv != null && userInfo.isAuthenticated())
        {
            if (userSessSrv.getUserDetails4mSession().isEmployee())
            {
                caseForm.setEmployee(true);
            }

            log.info("Processing of Case Form - UI layer :Begins....");

            // Any Validation Error(s) on the Form or Submission not possible
            if (!userSessSrv.SubmitCaseForm(caseForm))
            {
                log.info("Error in Case Form!");
                // Redirect to Error Processing of Form
                viewName = caseFormErrorRedirect;
            }
            else
            {
                // Fire Case Submission Event - To be processed Asyncronously

                TY_CaseFormAsync caseFormAsync = userSessSrv.getCurrentForm4Submission();
                // External/Internal User Pass to Asynch Thread as Session Scoped Service would
                // not be accessible in Asynch thread
                caseFormAsync.getCaseForm().setExternal(userSessSrv.getUserDetails4mSession().isExternal());
                EV_CaseFormSubmit eventCaseSubmit = new EV_CaseFormSubmit(this, caseFormAsync);
                applicationEventPublisher.publishEvent(eventCaseSubmit);
            }

            log.info("Processing of Case Form - UI layer :Ends....");
        }

        return viewName;

    }

    @PostMapping(value = "/saveCase", params = "action=upload")
    public String uploadAttachments(@ModelAttribute("caseForm") TY_Case_Form caseForm, Model model)
    {
        String viewName = caseFormView;

        List<String> attMsgs = Collections.emptyList();

        if (caseForm != null && attSrv != null && userSessSrv != null)
        {

            log.info("Processing of Case Attachment Upload Form - UI layer :Begins....");
            if (caseForm.getAttachment() != null)
            {
                if (StringUtils.hasText(caseForm.getAttachment().getOriginalFilename()))
                {
                    // Clear Attachment Service Session Messages for subsequent roundtip
                    attSrv.clearSessionMessages();
                    if (!attSrv.addAttachment(caseForm.getAttachment()))
                    {
                        // Attachment to Local Storage Persistence Error
                        attMsgs = attSrv.getSessionMessages();

                    }

                }

            }

            // Clear form for New Attachment as Current Attachment already in Container
            caseForm.setAttachment(null);

            Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                    .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString())).findFirst();
            if (cusItemO.isPresent() && catgTreeSrv != null)
            {

                model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                // Populate User Details
                TY_UserESS userDetails = new TY_UserESS();
                userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                model.addAttribute("userInfo", userDetails);

                if (userSessSrv.getUserDetails4mSession().isEmployee())
                {
                    caseForm.setEmployee(true);
                }

                // Scan for Template Load
                TY_CatgTemplates catgTemplate = catalogTreeSrv.getTemplates4Catg(caseForm.getCatgDesc(),
                        EnumCaseTypes.Learning);
                if (catgTemplate != null)
                {

                    // Set Questionnaire for Category
                    caseForm.setTemplate(catgTemplate.getQuestionnaire());

                }

                if (vhlpUISrv != null)
                {
                    model.addAllAttributes(
                            vhlpUISrv.getVHelpUIModelMap4LobCatg(EnumCaseTypes.Learning, caseForm.getCatgDesc()));
                }

                model.addAttribute("caseForm", caseForm);
                // also Place the form in Session
                userSessSrv.setCaseFormB4Submission(caseForm);

                model.addAttribute("formErrors", attMsgs);

                // also Upload the Catg. Tree as per Case Type
                model.addAttribute("catgsList",
                        catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

                model.addAttribute("attachments", attSrv.getAttachmentNames());

                // Attachment file Size
                model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());
            }

            log.info("Processing of Case Attachment Upload Form - UI layer :Ends....");
        }

        return viewName;

    }

    @GetMapping("/errForm/")
    public String showErrorCaseForm(Model model)
    {

        if ((StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations())
                && userSessSrv.getCurrentForm4Submission() != null)
        {

            Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                    .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString())).findFirst();
            if (cusItemO.isPresent() && catgTreeSrv != null)
            {

                model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                // Populate User Details
                TY_UserESS userDetails = new TY_UserESS();
                userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                model.addAttribute("userInfo", userDetails);

                // Populate Case Form Details
                TY_Case_Form caseForm = new TY_Case_Form();
                if (userSessSrv.getUserDetails4mSession().isEmployee())
                {
                    caseForm.setAccId(userSessSrv.getUserDetails4mSession().getEmployeeId()); // hidden
                }
                else
                {
                    caseForm.setAccId(userSessSrv.getUserDetails4mSession().getAccountId()); // hidden
                }

                caseForm.setCaseTxnType(cusItemO.get().getCaseType()); // hidden
                caseForm.setCatgDesc(userSessSrv.getCurrentForm4Submission().getCaseForm().getCatgDesc()); // Curr Catg
                caseForm.setDescription(userSessSrv.getCurrentForm4Submission().getCaseForm().getDescription()); // Curr
                                                                                                                 // Notes
                caseForm.setSubject(userSessSrv.getCurrentForm4Submission().getCaseForm().getSubject()); // Curr Subject

                if (StringUtils.hasText(userSessSrv.getCurrentForm4Submission().getCaseForm().getCountry()))
                {
                    caseForm.setCountry(userSessSrv.getCurrentForm4Submission().getCaseForm().getCountry());
                }

                if (StringUtils.hasText(userSessSrv.getCurrentForm4Submission().getCaseForm().getLanguage()))
                {
                    caseForm.setLanguage(userSessSrv.getCurrentForm4Submission().getCaseForm().getLanguage());
                }

                model.addAttribute("formErrors", userSessSrv.getFormErrors());

                // Not Feasible to have a Validation Error in Form and Attachment Persisted -
                // But just to handle theoratically in case there is an Error in Attachment
                // Persistence only- Remove the attachment otherwise let it persist
                if (CollectionUtils.isNotEmpty(userSessSrv.getMessageStack()))
                {
                    Optional<TY_Message> attErrO = userSessSrv.getMessageStack().stream()
                            .filter(e -> e.getMsgType().equals(EnumMessageType.ERR_ATTACHMENT)).findFirst();
                    if (!attErrO.isPresent())
                    {
                        // Attachment able to presist do not remove it from Current Payload
                        caseForm.setAttachment(userSessSrv.getCurrentForm4Submission().getCaseForm().getAttachment());

                    }
                }

                // Not Feasible to have a Validation Error in Form and Attachment Persisted -
                // But just to handle theoratically in case there is an Error in Attachment
                // Persistence only- Remove the attachment otherwise let it persist
                if (CollectionUtils.isNotEmpty(userSessSrv.getMessageStack()))
                {
                    Optional<TY_Message> attErrO = userSessSrv.getMessageStack().stream()
                            .filter(e -> e.getMsgType().equals(EnumMessageType.ERR_ATTACHMENT)).findFirst();
                    if (!attErrO.isPresent())
                    {
                        // Attachment able to presist do not remove it from Current Payload
                        caseForm.setAttachment(userSessSrv.getCurrentForm4Submission().getCaseForm().getAttachment());

                    }
                }

                if (vhlpUISrv != null)
                {
                    model.addAllAttributes(
                            vhlpUISrv.getVHelpUIModelMap4LobCatg(EnumCaseTypes.Learning, caseForm.getCatgDesc()));
                }

                model.addAttribute("caseForm", caseForm);

                // also Upload the Catg. Tree as per Case Type
                model.addAttribute("catgsList",
                        catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

                if (attSrv != null)
                {
                    if (CollectionUtils.isNotEmpty(attSrv.getAttachmentNames()))
                    {
                        model.addAttribute("attachments", attSrv.getAttachmentNames());
                    }
                }

                // Attachment file Size
                model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());

            }
            else
            {

                throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
            }

        }

        return caseFormView;
    }

    @PostMapping(value = "/saveCase", params = "action=catgChange")
    public String refreshCaseForm4Catg(@ModelAttribute("caseForm") TY_Case_Form caseForm, Model model)
    {

        String viewCaseForm = caseFormView;
        if (caseForm != null && userSessSrv != null)
        {

            // Normal Scenario - Catg. chosen Not relevant for Notes Template and/or
            // additional fields

            if ((StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                    || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                    && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations()))
            {

                Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                        .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString()))
                        .findFirst();
                if (cusItemO.isPresent() && catgTreeSrv != null)
                {

                    model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                    // Populate User Details
                    TY_UserESS userDetails = new TY_UserESS();
                    userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                    model.addAttribute("userInfo", userDetails);

                    // clear Form errors on each refresh or a New Case form request
                    if (CollectionUtils.isNotEmpty(userSessSrv.getFormErrors()))
                    {
                        userSessSrv.clearFormErrors();

                    }

                    // also Upload the Catg. Tree as per Case Type
                    model.addAttribute("catgsList",
                            catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

                    // Scan Current Catg for Templ. Load and or Additional Fields

                    // Scan for Template Load
                    TY_CatgTemplates catgTemplate = catalogTreeSrv.getTemplates4Catg(caseForm.getCatgDesc(),
                            EnumCaseTypes.Learning);
                    if (catgTemplate != null)
                    {

                        // Set Questionnaire for Category
                        caseForm.setTemplate(catgTemplate.getQuestionnaire());

                    }

                    if (vhlpUISrv != null)
                    {
                        model.addAllAttributes(
                                vhlpUISrv.getVHelpUIModelMap4LobCatg(EnumCaseTypes.Learning, caseForm.getCatgDesc()));
                    }

                    // Case Form Model Set at last
                    model.addAttribute("caseForm", caseForm);

                    if (attSrv != null)
                    {
                        if (CollectionUtils.isNotEmpty(attSrv.getAttachmentNames()))
                        {
                            model.addAttribute("attachments", attSrv.getAttachmentNames());
                        }
                    }

                    // Attachment file Size
                    model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());
                }
                else
                {

                    throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                    { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
                }

            }

        }

        return viewCaseForm;

    }

    @GetMapping("/removeAttachment/{fileName}")
    public String removeAttachmentCaseCreate(@PathVariable String fileName, Model model)
    {
        if (StringUtils.hasText(fileName) && attSrv != null && userSessSrv != null)
        {
            if (attSrv.checkIFExists(fileName))
            {
                attSrv.removeAttachmentByName(fileName);
            }

            // Populate the view

            Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                    .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString())).findFirst();
            if (cusItemO.isPresent() && catgTreeSrv != null)
            {

                model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                // Populate User Details
                TY_UserESS userDetails = new TY_UserESS();
                userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                model.addAttribute("userInfo", userDetails);

                // Get Case from Session Service

                TY_Case_Form caseForm = userSessSrv.getCaseFormB4Submission();

                if (caseForm != null)
                {

                    if (userSessSrv.getUserDetails4mSession().isEmployee())
                    {
                        caseForm.setEmployee(true);
                    }

                    // Clear form for New Attachment as Current Attachment already in Container
                    caseForm.setAttachment(null);

                    // Scan for Template Load
                    TY_CatgTemplates catgTemplate = catalogTreeSrv.getTemplates4Catg(caseForm.getCatgDesc(),
                            EnumCaseTypes.Learning);
                    if (catgTemplate != null)
                    {

                        // Set Questionnaire for Category
                        caseForm.setTemplate(catgTemplate.getQuestionnaire());

                    }

                    if (vhlpUISrv != null)
                    {
                        model.addAllAttributes(
                                vhlpUISrv.getVHelpUIModelMap4LobCatg(EnumCaseTypes.Learning, caseForm.getCatgDesc()));
                    }

                    model.addAttribute("caseForm", caseForm);

                    // also Upload the Catg. Tree as per Case Type
                    model.addAttribute("catgsList",
                            catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

                    model.addAttribute("attachments", attSrv.getAttachmentNames());

                    // Attachment file Size
                    model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());
                }

            }
        }
        return caseFormView;
    }

    @PostMapping(value = "/saveCaseReply", params = "action=saveCaseEdit")
    public String saveCaseReply(@ModelAttribute("caseEditForm") TY_CaseEdit_Form caseReplyForm, Model model)
            throws EX_ESMAPI, IOException
    {

        String viewName = caseListVWRedirect;
        if (caseReplyForm != null && userSessSrv != null)
        {

            log.info("Processing of Case Reply Form - UI layer :Begins....");

            // Any Validation Error(s) on the Form or Submission not possible
            if (!userSessSrv.SubmitCaseReply(caseReplyForm))
            {
                // Redirect to Error Processing of Form
                viewName = caseFormReplyErrorRedirect;
            }
            else
            {
                // Fire Case Submission Event - To be processed Asyncronously
                TY_CaseEditFormAsync caseEditFormAsync = userSessSrv.getCurrentReplyForm4Submission();

                // External/Internal User Pass to Asynch Thread as Session Scoped Service would
                // not be accessible in Asynch thread
                caseEditFormAsync.getCaseReply().setExternal(userSessSrv.getUserDetails4mSession().isExternal());
                EV_CaseReplySubmit eventCaseReplySubmit = new EV_CaseReplySubmit(this, caseEditFormAsync);
                applicationEventPublisher.publishEvent(eventCaseReplySubmit);
            }

            log.info("Processing of Case Form - UI layer :Ends....");
        }
        return viewName;
    }

    @PostMapping(value = "/saveCaseReply", params = "action=upload")
    public String uploadCaseReplyAttachment(@ModelAttribute("caseEditForm") TY_CaseEdit_Form caseReplyForm, Model model)
    {

        List<String> attMsgs = Collections.emptyList();
        if (caseReplyForm != null && userSessSrv != null)
        {
            if (StringUtils.hasText(caseReplyForm.getCaseDetails().getCaseGuid()))
            {

                // Get Case Details
                TY_CaseEdit_Form caseEditForm = userSessSrv
                        .getCaseDetails4Edit(caseReplyForm.getCaseDetails().getCaseGuid());

                // Super Impose Reply from User Form 4m Session
                caseEditForm.setReply(caseReplyForm.getReply());

                // Clear form for New Attachment as Current Attachment already in Container
                caseEditForm.setAttachment(null);

                // Populate User Details
                TY_UserESS userDetails = new TY_UserESS();
                userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                model.addAttribute("userInfo", userDetails);

                if (caseReplyForm.getAttachment() != null)
                {
                    if (StringUtils.hasText(caseReplyForm.getAttachment().getOriginalFilename()))
                    {
                        // Clear Attachment Service Session Messages for subsequent roundtip
                        attSrv.clearSessionMessages();
                        if (!attSrv.addAttachment(caseReplyForm.getAttachment()))
                        {
                            // Attachment to Local Storage Persistence Error
                            attMsgs = attSrv.getSessionMessages();

                        }

                    }

                }

                userSessSrv.setCaseEditFormB4Submission(caseEditForm);

                model.addAttribute("caseEditForm", caseEditForm);

                model.addAttribute("formErrors", attMsgs);

                model.addAttribute("attachments", attSrv.getAttachmentNames());
                // Attachment file Size
                model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());

            }
        }

        return caseFormReply;
    }

    @GetMapping("/caseReply/removeAttachment/{fileName}")
    public String removeAttachmentCaseReply(@PathVariable String fileName, Model model)
    {
        if (StringUtils.hasText(fileName) && attSrv != null && userSessSrv != null)
        {
            if (attSrv.checkIFExists(fileName))
            {
                attSrv.removeAttachmentByName(fileName);
            }

            // Populate User Details
            TY_UserESS userDetails = new TY_UserESS();
            userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
            model.addAttribute("userInfo", userDetails);

            model.addAttribute("formErrors", userSessSrv.getFormErrors());

            // Get Case Details
            TY_CaseEdit_Form caseEditForm = null;

            // Try to Get Case Edit Form from Upload Form from User Submit
            if (userSessSrv.getCurrentReplyForm4Submission() != null)
            {
                caseEditForm = userSessSrv.getCaseDetails4Edit(
                        userSessSrv.getCurrentReplyForm4Submission().getCaseReply().getCaseDetails().getCaseGuid());

                // Super Impose Reply from User Form 4m Session
                caseEditForm.setReply(userSessSrv.getCurrentReplyForm4Submission().getCaseReply().getReply());
                // Not Feasible to have a Validation Error in Form and Attachment Persisted -
                // But just to handle theoratically in case there is an Error in Attachment
                // Persistence only- Remove the attachment otherwise let it persist
                if (CollectionUtils.isNotEmpty(userSessSrv.getMessageStack()))
                {
                    Optional<TY_Message> attErrO = userSessSrv.getMessageStack().stream()
                            .filter(e -> e.getMsgType().equals(EnumMessageType.ERR_ATTACHMENT)).findFirst();
                    if (attErrO.isPresent())
                    {
                        // Attachment able to presist do not remove it from Current Payload
                        caseEditForm.setAttachment(null);

                    }
                }
            }
            else
            {
                caseEditForm = userSessSrv
                        .getCaseDetails4Edit(userSessSrv.getCaseEditFormB4Submission().getCaseDetails().getCaseGuid());
                // Super Impose Reply from User Form 4m Session
                caseEditForm.setReply(userSessSrv.getCaseEditFormB4Submission().getReply());
            }

            if (caseEditForm != null)
            {

                model.addAttribute("caseEditForm", caseEditForm);

                model.addAttribute("attachments", attSrv.getAttachmentNames());

                // Attachment file Size
                model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());
            }
        }

        return caseFormReply;

    }

    @GetMapping("/errCaseReply/")
    public String showErrorCaseReplyForm(Model model)
    {
        if (userSessSrv != null)
        {

            if (userSessSrv.getCurrentReplyForm4Submission() != null && StringUtils.hasText(
                    userSessSrv.getCurrentReplyForm4Submission().getCaseReply().getCaseDetails().getCaseGuid()))
            {

                // Populate User Details
                TY_UserESS userDetails = new TY_UserESS();
                userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                model.addAttribute("userInfo", userDetails);

                model.addAttribute("formErrors", userSessSrv.getFormErrors());

                // Get Case Details
                TY_CaseEdit_Form caseEditForm = userSessSrv.getCaseDetails4Edit(
                        userSessSrv.getCurrentReplyForm4Submission().getCaseReply().getCaseDetails().getCaseGuid());

                if (caseEditForm != null)
                {
                    // Super Impose Reply from User Form 4m Session
                    caseEditForm.setReply(userSessSrv.getCurrentReplyForm4Submission().getCaseReply().getReply());
                    // Not Feasible to have a Validation Error in Form and Attachment Persisted -
                    // But just to handle theoratically in case there is an Error in Attachment
                    // Persistence only- Remove the attachment otherwise let it persist
                    if (CollectionUtils.isNotEmpty(userSessSrv.getMessageStack()))
                    {
                        Optional<TY_Message> attErrO = userSessSrv.getMessageStack().stream()
                                .filter(e -> e.getMsgType().equals(EnumMessageType.ERR_ATTACHMENT)).findFirst();
                        if (attErrO.isPresent())
                        {
                            // Attachment able to presist do not remove it from Current Payload
                            caseEditForm.setAttachment(null);

                        }
                    }

                    model.addAttribute("caseEditForm", caseEditForm);

                    model.addAttribute("attachments", attSrv.getAttachmentNames());

                    // Attachment file Size
                    model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());
                }

            }
            else
            {

                throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_DET_FETCH", new Object[]
                { userSessSrv.getCurrentReplyForm4Submission().getCaseReply().getCaseDetails().getCaseGuid() },
                        Locale.ENGLISH));
            }
        }
        return caseFormReply;

    }

    @GetMapping("/caseDetails/{caseID}")
    public String getCaseDetails(@PathVariable String caseID, Model model)
    {
        if (StringUtils.hasText(caseID))
        {
            if (userSessSrv != null)
            {

                try
                {

                    // Populate User Details
                    TY_UserESS userDetails = new TY_UserESS();
                    userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                    model.addAttribute("userInfo", userDetails);

                    // Get Case Details
                    TY_CaseEdit_Form caseEditForm = userSessSrv.getCaseDetails4Edit(caseID);
                    if (caseEditForm != null)
                    {
                        model.addAttribute("caseEditForm", caseEditForm);
                        if (CollectionUtils.isNotEmpty(caseEditForm.getCaseDetails().getNotes()))
                        {
                            log.info("# External Notes Bound for Case ID - "
                                    + caseEditForm.getCaseDetails().getNotes().size());

                        }

                        // Initialize Attachments Session Service
                        if (attSrv != null)
                        {
                            attSrv.initialize();
                        }

                        // Attachment file Size
                        model.addAttribute("attSize", rlConfig.getAllowedSizeAttachmentMB());
                    }
                }
                catch (Exception e)
                {
                    return "error";
                }
            }
        }

        return caseFormReply;
    }

}
