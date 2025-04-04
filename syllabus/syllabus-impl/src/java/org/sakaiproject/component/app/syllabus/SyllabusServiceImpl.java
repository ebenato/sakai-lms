/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008, 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.component.app.syllabus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.api.app.syllabus.GatewaySyllabus;
import org.sakaiproject.api.app.syllabus.SyllabusAttachment;
import org.sakaiproject.api.app.syllabus.SyllabusData;
import org.sakaiproject.api.app.syllabus.SyllabusItem;
import org.sakaiproject.api.app.syllabus.SyllabusManager;
import org.sakaiproject.api.app.syllabus.SyllabusService;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.Edit;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.NotificationEdit;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.BaseResourcePropertiesEdit;
import org.sakaiproject.util.Validator;
import org.sakaiproject.util.cover.LinkMigrationHelper;
import org.sakaiproject.util.MergeConfig;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.transaction.annotation.Transactional;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rshastri TODO To change the template for this generated type comment go to Window -
 *         Preferences - Java - Code Style - Code Templates
 */
@Slf4j
public class SyllabusServiceImpl implements SyllabusService, EntityTransferrer
{
  private static final String SYLLABUS = "syllabus";
  private static final String SYLLABUS_ID = "id";
  private static final String SYLLABUS_USER_ID = "userID";
  private static final String SYLLABUS_REDIRECT_URL = "redirectUrl";
  private static final String SYLLABUS_CONTEXT_ID = "contextId";
  private static final String SYLLABUS_DATA = "syllabus_data";
  private static final String SYLLABUS_DATA_TITLE = "title";
  private static final String SYLLABUS_DATA_POSITION = "position";
  private static final String SYLLABUS_DATA_VIEW = "view";
  private static final String SYLLABUS_DATA_ID = "syllabus_id";
  private static final String SYLLABUS_DATA_EMAIL_NOTIFICATION = "emailNotification";
  private static final String SYLLABUS_DATA_STATUS = "status";
  private static final String SYLLABUS_DATA_ASSET = "asset";
  private static final String SYLLABUS_ATTACHMENT = "attachment";
  private static final String PAGE_ARCHIVE = "pageArchive";
  private static final String SITE_NAME = "siteName";
  private static final String SITE_ID = "siteId";
  private static final String SITE_ARCHIVE = "siteArchive";
  private static final String PAGE_NAME = "pageName";
  private static final String PAGE_ID = "pageId";
  /** Dependency: a SyllabusManager. */
  private SyllabusManager syllabusManager;
  private FunctionManager functionManager;
  private ContentHostingService contentHostingService;
  private SiteService siteService;
  private EntityManager entityManager;
  @Setter private TimeService timeService;
  @Setter private UserDirectoryService userDirectoryService;
  @Setter private ToolManager toolManager;
  @Setter private SessionManager sessionManager;
  @Setter private EventTrackingService eventTrackingService;
  @Setter private LTIService ltiService;

  protected NotificationService notificationService = null;
  protected String m_relativeAccessPoint = null;
  private SecurityService securityService;
  
//sakai2 -- add init and destroy methods  
	public void init()
	{
	  
	  m_relativeAccessPoint = REFERENCE_ROOT;
	  
	  NotificationEdit edit = notificationService.addTransientNotification();
	  
	  edit.setFunction(EVENT_SYLLABUS_POST_NEW);
	  edit.addFunction(EVENT_SYLLABUS_POST_CHANGE);
	  edit.addFunction(EVENT_SYLLABUS_DELETE_POST);
	  edit.addFunction(EVENT_SYLLABUS_READ);
	  edit.addFunction(EVENT_SYLLABUS_DRAFT_NEW);
	  edit.addFunction(EVENT_SYLLABUS_DRAFT_CHANGE);
	  
	  edit.setResourceFilter(getAccessPoint(true));
	  
	  edit.setAction(new SiteEmailNotificationSyllabus());

	  entityManager.registerEntityProducer(this, REFERENCE_ROOT);	
	  functionManager.registerFunction(SECURE_ADD_ITEM);
	  functionManager.registerFunction(SECURE_BULK_ADD_ITEM);
	  functionManager.registerFunction(SECURE_BULK_EDIT_ITEM);
	  functionManager.registerFunction(SECURE_REDIRECT);
	}

	public void destroy()
	{
	}

  /** Dependency: a SyllabusManager component. */
  public void setSyllabusManager(SyllabusManager syllabusManager)
  {
    this.syllabusManager = syllabusManager;
  }
  
  public void setFunctionManager(FunctionManager functionManager) {
	this.functionManager = functionManager;
  }


	public void setContentHostingService(ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}
	
	public void setSiteService(SiteService siteService) {
		this.siteService= siteService;
	}
	
	public void setSecurityService(SecurityService securityService) {
		this.securityService = securityService;
	}
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}
 
  /*
   * (non-Javadoc)
   * 
   * @see org.sakaiproject.service.legacy.entity.ResourceService#getLabel()
   */
  public String getLabel()
  {
    return "syllabus";
  }

	/**
	 * {@inheritDoc}
	 */
	public boolean willArchiveMerge()
	{
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean willImport()
	{
		return true;
	}

	/**
	 * from StringUtil.java
	 */
	protected String[] split(String source, String splitter)
	{
		// hold the results as we find them
		Vector rv = new Vector();
		int last = 0;
		int next = 0;
		do
		{
			// find next splitter in source
			next = source.indexOf(splitter, last);
			if (next != -1)
			{
				// isolate from last thru before next
				rv.add(source.substring(last, next));
				last = next + splitter.length();
			}
		}
		while (next != -1);
		if (last < source.length())
		{
			rv.add(source.substring(last, source.length()));
		}

		// convert to array
		return (String[]) rv.toArray(new String[rv.size()]);

	} // split

	/**
	 * {@inheritDoc}
	 */
	public boolean parseEntityReference(String reference, Reference ref)
	{
		if (reference.startsWith(REFERENCE_ROOT))
		{
			// /syllabus/siteid/syllabusid
			String[] parts = split(reference, Entity.SEPARATOR);

			String subType = null;
			String context = null;
			String id = null;
			String container = null;

			// the first part will be null, then next the service, the third will be "calendar" or "event"
			if (parts.length > 2)
			{
				// the site/context
				context = parts[2];

				// the id
				if (parts.length > 3)
				{
					id = parts[3];
				}
			}

			ref.set(APPLICATION_ID, subType, id, container, context);

			return true;
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public Collection getEntityAuthzGroups(Reference ref, String userId)
	{
	//permission convert
		Collection rv = new Vector();

		try
		{
			if (SYLLABUS.equals(ref.getSubType()))
			{
				rv.add(ref.getReference());
				
				ref.addSiteContextAuthzGroup(rv);
			}
		}
		catch (Exception e) 
		{
			log.error("SyllabusServiceImpl:getEntityAuthzGroups - " + e);
		}

		return rv;

	}

  /*
   * (non-Javadoc)
   * 
   * @see org.sakaiproject.service.legacy.entity.ResourceService#archive(java.lang.String,
   *      org.w3c.dom.Document, java.util.Stack, java.lang.String,
   *      org.sakaiproject.service.legacy.entity.ReferenceVector)
   */
  public String archive(String siteId, Document doc, Stack stack, String arg3,
      List attachments)
  {

	StringBuilder results = new StringBuilder();

    try
    {
      int syDataCount = 0;
      results.append("archiving " + getLabel() + " context "
          + Entity.SEPARATOR + siteId + Entity.SEPARATOR
          + siteService.MAIN_CONTAINER + ".\n");
      // start with an element with our very own (service) name
      Element element = doc.createElement(SyllabusService.class.getName());
      ((Element) stack.peek()).appendChild(element);
      stack.push(element);
      if (siteId != null && siteId.trim().length() > 0)
      {
        Element siteElement = doc.createElement(SITE_ARCHIVE);
        siteElement.setAttribute(SITE_NAME, siteService.getSite(siteId).getId());
        siteElement.setAttribute(SITE_ID, siteService.getSite(siteId).getTitle());
        SyllabusItem syllabusItem = syllabusManager.getSyllabusItemByContextId(siteId);
        if (syllabusItem != null)
        {
                Element syllabus = doc.createElement(SYLLABUS);
                syllabus.setAttribute(SYLLABUS_ID, syllabusItem
                    .getSurrogateKey().toString());
                syllabus.setAttribute(SYLLABUS_USER_ID, syllabusItem
                    .getUserId());
                syllabus.setAttribute(SYLLABUS_CONTEXT_ID, syllabusItem
                    .getContextId());
                syllabus.setAttribute(SYLLABUS_REDIRECT_URL, syllabusItem
                    .getRedirectURL());

                Set syllabi = syllabusManager
                    .getSyllabiForSyllabusItem(syllabusItem);

                if (syllabi != null && !syllabi.isEmpty())
                {
                  Iterator syllabiIter = syllabi.iterator();
                  while (syllabiIter.hasNext())
                  {
                    SyllabusData syllabusData = (SyllabusData) syllabiIter
                        .next();
                    if (syllabusData != null)
                    {
                      syDataCount++;
                      Element syllabus_data = doc.createElement(SYLLABUS_DATA);
                      syllabus_data.setAttribute(SYLLABUS_DATA_ID, syllabusData
                          .getSyllabusId().toString());
                      syllabus_data.setAttribute(SYLLABUS_DATA_POSITION,
                          syllabusData.getPosition().toString());
                      syllabus_data.setAttribute(SYLLABUS_DATA_TITLE,
                          syllabusData.getTitle());
                      syllabus_data.setAttribute(SYLLABUS_DATA_VIEW,
                          syllabusData.getView());
                      syllabus_data.setAttribute(SYLLABUS_DATA_STATUS,
                          syllabusData.getStatus());
                      syllabus_data.setAttribute(
                          SYLLABUS_DATA_EMAIL_NOTIFICATION, syllabusData
                              .getEmailNotification());
                      Element asset = doc.createElement(SYLLABUS_DATA_ASSET);

                      Set<SyllabusAttachment> syllabusAttachments = syllabusManager.getSyllabusAttachmentsForSyllabusData(syllabusData);
                      for (SyllabusAttachment s : syllabusAttachments) {
                          ContentResource cr = null;
                          try {
                                  cr = contentHostingService.getResource(s.getAttachmentId());
                          } catch (PermissionException e) {
                                  log.warn("Permission error fetching resource: " + s.getAttachmentId());
                          } catch (TypeException e) {
                        	  log.warn("TypeException error fetching resource: " + s.getAttachmentId());
                          }

                          if (cr != null) {
                                  Reference ref = entityManager.newReference(cr.getReference());
                                  attachments.add(ref);
                                  Element a = doc.createElement("attachment");
                                  syllabus_data.appendChild(a);
                                  a.setAttribute("relative-url", ref.getReference());
                          }
                      }

                      try
                      {
                        String encoded = new String(Base64.encodeBase64(syllabusData.getAsset().getBytes()),"UTF-8");
                        asset.setAttribute("syllabus_body-html", encoded);
                      }
                      catch(Exception e)
                      {
                        log.warn("Encode Syllabus - " + e);
                      }
                      
                      
                      syllabus_data.appendChild(asset);
                      syllabus.appendChild(syllabus_data);

                    }
                  }

            siteElement.appendChild(syllabus);      
          }
          results.append("archiving " + getLabel() + ": (" + syDataCount
              + ") syllabys items archived successfully.\n");
        }
        else
        {
          results.append("archiving " + getLabel()
              + ": empty syllabus archived.\n");
        }
        ((Element) stack.peek()).appendChild(siteElement);
        stack.push(siteElement);
      }
      stack.pop();

    }
    catch (DOMException e)
    {
      log.error(e.getMessage(), e);
    }
    catch (IdUnusedException e)
    {
      log.error(e.getMessage(), e);
    }
    return results.toString();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.sakaiproject.service.legacy.entity.ResourceService#merge(java.lang.String,
   *      org.w3c.dom.Element, java.lang.String, java.lang.String, java.util.Map, java.util.HashMap,
   *      java.util.Set)
   */
  @Transactional
  public String merge(String siteId, Element root, String archivePath, String fromSiteId, MergeConfig mcx)
  {

	log.debug("merge archiveContext={} archiveServerUrl={}", mcx.archiveContext, mcx.archiveServerUrl);

    // Get the existing titles for duplicate removal
    Set<String> syllabusTitles = new LinkedHashSet<>();
    SyllabusItem theItem = syllabusManager.getSyllabusItemByContextId(siteId);

    if (theItem != null) {
      Set<SyllabusData> syllabi = syllabusManager.getSyllabiForSyllabusItem(theItem);

      if (syllabi != null && !syllabi.isEmpty()) {
        syllabusTitles = syllabi.stream()
          .filter(Objects::nonNull)
          .map(SyllabusData::getTitle)
          .filter(Objects::nonNull)
          .collect(Collectors.toCollection(LinkedHashSet::new));
      }
    }

    // buffer for the results log
	StringBuilder results = new StringBuilder();
    // populate SyllabusItem
    int syDataCount = 0;
    SyllabusItem syItem = null;
    if (siteId != null && siteId.trim().length() > 0)
    {
      try
      {
        NodeList allChildrenNodes = root.getChildNodes();
        int length = allChildrenNodes.getLength();
        for (int i = 0; i < length; i++)
        {
          Node siteNode = allChildrenNodes.item(i);
          if (siteNode.getNodeType() == Node.ELEMENT_NODE)
          {
            Element siteElement = (Element) siteNode;
            if (siteElement.getTagName().equals(SITE_ARCHIVE))
            {
                    NodeList syllabusNodes = siteElement.getChildNodes();
                    int lengthSyllabusNodes = syllabusNodes.getLength();
                    for (int sn = 0; sn < lengthSyllabusNodes; sn++)
                    {
                      Node syNode = syllabusNodes.item(sn);
                      if (syNode.getNodeType() == Node.ELEMENT_NODE)
                      {
                        Element syElement = (Element) syNode;
                        if (syElement.getTagName().equals(SYLLABUS))
                        {
                          //create a page and all syllabus tool to the page
                          String page = addSyllabusToolToPage(siteId,siteElement
                            .getAttribute(SITE_NAME));
                          SyllabusItem syllabusItem = syllabusManager.getSyllabusItemByContextId(page);
                          if(syllabusItem == null)
                          {
                            syllabusItem = syllabusManager
                              .createSyllabusItem(userDirectoryService
                                  .getCurrentUser().getId(), page, syElement
                                  .getAttribute(SYLLABUS_REDIRECT_URL));
                          } 
                          //added htripath: get imported redirecturl, even if syllabus item is existing.
                          else{
                            if (syElement.getAttribute(SYLLABUS_REDIRECT_URL) !=null){
                              syllabusItem.setRedirectURL(syElement.getAttribute(SYLLABUS_REDIRECT_URL));
                              syllabusManager.saveSyllabusItem(syllabusItem) ;
                            }                            
                          }
                          //
                          NodeList allSyllabiNodes = syElement.getChildNodes();
                          int lengthSyllabi = allSyllabiNodes.getLength();
                          for (int j = 0; j < lengthSyllabi; j++)
                          {
                            Node child2 = allSyllabiNodes.item(j);
                            if (child2.getNodeType() == Node.ELEMENT_NODE)
                            {
                              Element syDataElement = (Element) child2;
                              if (SYLLABUS_DATA.equals(syDataElement.getTagName()))
                              {
                                List<String> attachStringList = new ArrayList<String>();

                                String syllabusTitle = syDataElement.getAttribute(SYLLABUS_DATA_TITLE);
                                if ( syllabusTitles.contains(syllabusTitle) ) continue;
                                syDataCount = syDataCount + 1;
                                SyllabusData syData = new SyllabusData();
                                syData.setView(syDataElement
                                    .getAttribute(SYLLABUS_DATA_VIEW));
                                syData.setTitle(syDataElement
                                    .getAttribute(SYLLABUS_DATA_TITLE));
                                syData.setStatus(syDataElement
                                    .getAttribute(SYLLABUS_DATA_STATUS));
                                syData
                                    .setEmailNotification(syDataElement
                                        .getAttribute(SYLLABUS_DATA_EMAIL_NOTIFICATION));

                                NodeList allAssetNodes = syDataElement.getChildNodes();
                                int lengthSyData = allAssetNodes.getLength();
                                for (int k = 0; k < lengthSyData; k++)
                                {
                                  Node child3 = allAssetNodes.item(k);
                                  if (child3.getNodeType() == Node.ELEMENT_NODE)
                                  {
                                    Element assetEle = (Element) child3;
                                    if (SYLLABUS_DATA_ASSET.equals(assetEle.getTagName()))
                                    {
                                      String charset = trimToNull(assetEle.getAttribute("charset"));
                                      if (charset == null) charset = "UTF-8";
                                      
                                      String body = trimToNull(assetEle.getAttribute("syllabus_body-html"));
                                      if (body != null)
                                      {
                                        try
                                        {
                                          byte[] decoded = Base64.decodeBase64(body.getBytes("UTF-8"));
                                          body = new String(decoded, charset);
                                          body = ltiService.fixLtiLaunchUrls(body, siteId, mcx);
                                          body = LinkMigrationHelper.migrateLinksInMergedRTE(siteId, mcx, body);
                                        }
                                        catch (Exception e)
                                        {
                                          log.warn("Decode Syllabus: " + e);
                                        }
                                      }
                                      
                                      if (body == null) body = "";
                                      
                                      String ret;
                                      ret = trimToNull(body);
                                      
                                      syData.setAsset(ret);
                                    }
                                    else if (SYLLABUS_ATTACHMENT.equals(assetEle.getTagName()))
                                    {
                                      Element attachElement = (Element) child3;
                                      String oldUrl = attachElement.getAttribute("relative-url");
                                      String newUrl = transferAttachment(oldUrl, siteId, mcx);
                                      if (newUrl != null)
                                      {
                                        attachElement.setAttribute("relative-url", Validator.escapeQuestionMark(newUrl));
                                        attachStringList.add(Validator.escapeQuestionMark(newUrl));
                                      }
                                    }
                                  }
                                }

                                int initPosition = syllabusManager.findLargestSyllabusPosition(syllabusItem).intValue() + 1;
                                syData = syllabusManager.createSyllabusDataObject(syData.getTitle(), (new Integer(initPosition)), syData.getAsset(),
                                        syData.getView(), syData.getStatus(),
                                        syData.getEmailNotification(), syData.getStartDate(), syData.getEndDate(), syData.getLinkCalendar(),
                                        syData.getCalendarEventIdStartDate(), syData.getCalendarEventIdEndDate(), syllabusItem);
                                syData.setView("yes");  // or attachments don't get saved

                                Set<SyllabusAttachment> attachSet = new TreeSet<SyllabusAttachment>();
                                for(int m=0; m<attachStringList.size(); m++)
                                {
                                  String attachmentId = attachStringList.get(m);
                                  if (attachmentId.startsWith("/content")) {
                                    attachmentId = attachmentId.replaceFirst("/content", "");
                                  }
                                  ContentResource cr = contentHostingService.getResource(attachmentId);
                                  SyllabusAttachment thisSyllabusAttach = syllabusManager.createSyllabusAttachmentObject(
                                    cr.getId(),
                                    cr.getProperties().getProperty(ResourceProperties.PROP_DISPLAY_NAME));
                                  thisSyllabusAttach.setSyllabusData(syData);
                                  attachSet.add(thisSyllabusAttach);

                                }
                                syData.setAttachments(attachSet);

                                syllabusManager.addSyllabusToSyllabusItem(syllabusItem, syData, false);
                              }
                            }
                          }
                        }
                      }
                    }
            }
          }

        }
        results.append("merging syllabus " + siteId + " (" + syDataCount
            + ") syllabus items.\n");

      }
      catch (DOMException e)
      {
        log.error(e.getMessage(), e);
        results.append("merging " + getLabel()
            + " failed during xml parsing.\n");

      }
      catch (Exception e)
      {
        log.error(e.getMessage(), e);
        results.append("merging " + getLabel() + " failed.\n");
      }
    }

    return results.toString();
  }

  private String transferAttachment(String oAttachmentId, String toContext, MergeConfig mcx) {
    String toolTitle = toolManager.getTool("sakai.syllabus").getTitle();
    try {
        ContentResource attachment = contentHostingService.copyAttachment(oAttachmentId, toContext, toolTitle, mcx);
        if ( attachment != null ) {
            return attachment.getReference();
        }
    } catch (IdUnusedException | TypeException | PermissionException e) {
        log.error("Error copying attachment: {}", e.getMessage());
    }
    return null;
  }

  private String addSyllabusToolToPage(String siteId,String pageName)
  {
    return siteId;
  }


  public void setNotificationService(NotificationService notificationService)
	{
		this.notificationService = notificationService;
	}

  protected String getAccessPoint(boolean relative)
	{
		return (relative ? "" : ServerConfigurationService.getAccessUrl()) + m_relativeAccessPoint;
	}
  
  public void postNewSyllabus(SyllabusData data)
  {
    BaseResourceEdit bre = new BaseResourceEdit(data.getSyllabusId().toString(), data);
    
    addLiveSyllabusProperties(bre);
    
    bre.setEvent(EVENT_SYLLABUS_POST_NEW);
    
    String emailNotify = data.getEmailNotification();
    
    int priority;
    
    if(emailNotify.equalsIgnoreCase("none"))
    {
      priority = NotificationService.NOTI_NONE;
    }
    else if(emailNotify.equalsIgnoreCase("high"))
    {
      priority = NotificationService.NOTI_REQUIRED;
    }
    else if(emailNotify.equalsIgnoreCase("low"))
    {
      priority = NotificationService.NOTI_OPTIONAL;
    }
    else
    {
      priority = NotificationService.NOTI_NONE;
    }

		Event event =
		  	eventTrackingService.newEvent(bre.getEvent(), bre.getReference(), 
			    true, priority);
		
		eventTrackingService.post(event);
  }
  
  public class BaseResourceEdit implements Entity, Edit
  {
		protected String m_id = null;

		protected String m_event = null;

		protected boolean m_active = false;

		protected boolean m_isRemoved = false;

		protected boolean m_bodyUpdated = false;
		
		protected ResourcePropertiesEdit m_properties = null;
		
		protected String m_reference = null;
		
		protected SyllabusData m_data = null;

		public BaseResourceEdit(String id, SyllabusData data)
		{
	    Placement placement = toolManager.getCurrentPlacement();
			String currentSiteId = placement.getContext();
			
			m_id = id;
			
			m_data = data;
			
			m_reference = Entity.SEPARATOR + currentSiteId + Entity.SEPARATOR + m_id;
			
			m_properties = new BaseResourcePropertiesEdit();
			
			m_properties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, data.getTitle());
		}
		
		//permission convert
		public BaseResourceEdit(String id, SyllabusData data, String siteId)
		{
			m_id = id;
			
			m_data = data;
			
			m_reference = Entity.SEPARATOR + siteId + Entity.SEPARATOR + m_id;
			
			m_properties = new BaseResourcePropertiesEdit();
			
			m_properties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, data.getTitle());
		}

		//permission convert
		public BaseResourceEdit(String siteId)
		{
			m_id = null;
			
			m_data = null;
			
			m_reference = Entity.SEPARATOR + siteId;
			
			m_properties = new BaseResourcePropertiesEdit();
			
			m_properties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, "");
		}

		public String getUrl()
    {
			return getAccessPoint(false) + "/" + m_id;
    }

    public String getReference()
    {
        return getAccessPoint(true) + m_reference;
    }

	/**
	 * @inheritDoc
	 */
	public String getReference(String rootProperty)
	{
		return getReference();
	}

	/**
	 * @inheritDoc
	 */
	public String getUrl(String rootProperty)
	{
		return getUrl();
	}

	public String getId()
    {
      return m_id;
    }

    public ResourceProperties getProperties()
    {
      return m_properties;
    }

	public Element toXml(Document doc, Stack stack)
    {
			Element syllabus = doc.createElement("syllabus");

			if (stack.isEmpty())
			{
				doc.appendChild(syllabus);
			}
			else
			{
				((Element) stack.peek()).appendChild(syllabus);
			}

			stack.push(syllabus);

			syllabus.setAttribute("id", m_id);
			syllabus.setAttribute("subject", m_data.getTitle());
			syllabus.setAttribute("body", m_data.getAsset());

			m_properties.toXml(doc, stack);

			stack.pop();

			return syllabus;

    }

    public boolean isActiveEdit()
    {
      return m_active;
    }

    public ResourcePropertiesEdit getPropertiesEdit()
    {
      return m_properties;
    }
    
    protected void closeEdit()
    {
      m_active = false;
    }
    
    protected void activate()
    {
      m_active = true;
    }
    
		protected String getEvent()
		{
			return m_event;
		}
		
		protected void setEvent(String event)
		{
		  m_event = event; 
		}
  }
  
	protected void addLiveSyllabusProperties(BaseResourceEdit r)
	{
		ResourcePropertiesEdit p = r.getPropertiesEdit();

		String current = sessionManager.getCurrentSessionUserId();
		p.addProperty(ResourceProperties.PROP_CREATOR, current);
		p.addProperty(ResourceProperties.PROP_MODIFIED_BY, current);

		String now = timeService.newTime().toString();
		p.addProperty(ResourceProperties.PROP_CREATION_DATE, now);
		p.addProperty(ResourceProperties.PROP_MODIFIED_DATE, now);

		p.addProperty(ResourceProperties.PROP_IS_COLLECTION, "false");
	}
	
	public void postChangeSyllabus(SyllabusData data)
	{
    BaseResourceEdit bre = new BaseResourceEdit(data.getSyllabusId().toString(), data);
    
    addLiveSyllabusProperties(bre);
    
    bre.setEvent(EVENT_SYLLABUS_POST_CHANGE);
    
    String emailNotify = data.getEmailNotification();
    
    int priority;
    
    if(emailNotify.equalsIgnoreCase("none"))
    {
      priority = NotificationService.NOTI_NONE;
    }
    else if(emailNotify.equalsIgnoreCase("high"))
    {
      priority = NotificationService.NOTI_REQUIRED;
    }
    else if(emailNotify.equalsIgnoreCase("low"))
    {
      priority = NotificationService.NOTI_OPTIONAL;
    }
    else
    {
      priority = NotificationService.NOTI_NONE;
    }

		Event event =
		  	eventTrackingService.newEvent(bre.getEvent(), bre.getReference(), 
			    true, priority);
		
		eventTrackingService.post(event);
	}

	public void deletePostedSyllabus(SyllabusData data)
	{
		Placement placement = toolManager.getCurrentPlacement();
		String siteId = placement.getContext();
		deletePostedSyllabus(data, siteId);
	}
	
	public void deletePostedSyllabus(SyllabusData data, String siteId)
	{
    BaseResourceEdit bre = new BaseResourceEdit(data.getSyllabusId().toString(), data, siteId);
    
    addLiveSyllabusProperties(bre);
    
    bre.setEvent(EVENT_SYLLABUS_DELETE_POST);
    
    String emailNotify = data.getEmailNotification();
    
    int priority;
    
//for adding more logging info and not send out email notification
//    if(emailNotify.equalsIgnoreCase("none"))
//    {
//      priority = NotificationService.NOTI_NONE;
//    }
//    else if(emailNotify.equalsIgnoreCase("high"))
//    {
//      priority = NotificationService.NOTI_REQUIRED;
//    }
//    else if(emailNotify.equalsIgnoreCase("low"))
//    {
//      priority = NotificationService.NOTI_OPTIONAL;
//    }
//    else
//    {
//      priority = NotificationService.NOTI_NONE;
//    }
    priority = NotificationService.NOTI_NONE;

		Event event =
		  	eventTrackingService.newEvent(bre.getEvent(), bre.getReference(), 
			    true, priority);
		
		eventTrackingService.post(event);
	}
	
	public String trimToNull(String value)
	{
		if (value == null) return null;
		value = value.trim();
		if (value.length() == 0) return null;
		return value;

	}
	
	public List getMessages(String id)
	{
	  ArrayList list = new ArrayList();
	  
	  SyllabusItem syllabusItem = syllabusManager.getSyllabusItemByContextId(id);
	  if(syllabusItem == null)
	  {
	    return null;
	  }
	  Set listSet = syllabusManager.getSyllabiForSyllabusItem(syllabusItem);
	  Iterator iter = listSet.iterator();
	  while(iter.hasNext())
	  {
	    SyllabusData sd = (SyllabusData)iter.next();
	    if(sd.getView().equalsIgnoreCase("yes") && (sd.getStatus().equalsIgnoreCase(SyllabusData.ITEM_POSTED)))
	    {
	      ArrayList attachList = new ArrayList();
	  	  Set attachSet = syllabusManager.getSyllabusAttachmentsForSyllabusData(sd);
	  	  Iterator attachIter = attachSet.iterator();
	  	  while(attachIter.hasNext())
	  	  {
	  	    attachList.add((SyllabusAttachment)attachIter.next());
	  	  }

	      GatewaySyllabus gs = new GatewaySyllabusImpl(sd, attachList);
	      
	      list.add(gs);
	    }
	  }
	  
	  return list;
	}

	public String[] myToolIds()
	{
		String[] toolIds = { "sakai.syllabus" };
		return toolIds;
	}

	@Override
	public List<Map<String, String>> getEntityMap(String fromContext) {

		SyllabusItem item = syllabusManager.getSyllabusItemByContextId(fromContext);

		return syllabusManager.getSyllabiForSyllabusItem(item).stream()
			.map(d -> Map.of("id", d.getSyllabusId().toString(), "title", d.getTitle())).collect(Collectors.toList());
	}

	@Transactional
	public Map<String, String> transferCopyEntities(String fromContext, String toContext, List<String> ids, List<String> options)
	{
		Map<String, String> transversalMap = new HashMap<String, String>();
		
		try 
		{
			log.debug("transfer copy syllabus itmes by transferCopyEntities");
			String fromPage = fromContext;
			SyllabusItem fromSyllabusItem = syllabusManager
					.getSyllabusItemByContextId(fromPage);
			if (fromSyllabusItem != null) 
			{
				Set<SyllabusData> fromSyDataSet = syllabusManager.getSyllabiForSyllabusItem(fromSyllabusItem);
				if (CollectionUtils.isNotEmpty(fromSyDataSet) || fromSyllabusItem.getRedirectURL() != null) 
				{
					if (CollectionUtils.isNotEmpty(ids)) {
						fromSyDataSet = fromSyDataSet.stream().filter(d -> ids.contains(d.getSyllabusId().toString())).collect(Collectors.toSet());
					}

					String toPage = addSyllabusToolToPage(toContext, siteService.getSite(toContext).getTitle());
					SyllabusItem toSyItem = syllabusManager.getSyllabusItemByContextId(toPage);
					String redirectUrl = fromSyllabusItem.getRedirectURL();
					if (StringUtils.contains(redirectUrl, fromContext))
					{
							redirectUrl = redirectUrl.replaceAll(fromContext, toContext);
					}
					if (toSyItem == null) 
					{
						toSyItem = syllabusManager.createSyllabusItem(
								userDirectoryService.getCurrentUser().getId(),
								toPage, redirectUrl);
					}
					else if (fromSyllabusItem.getRedirectURL() !=null) {
	                    toSyItem.setRedirectURL(redirectUrl);
	                    syllabusManager.saveSyllabusItem(toSyItem);
	                }

					for (SyllabusData sd : fromSyDataSet)
					{
						Integer positionNo = new Integer(syllabusManager
								.findLargestSyllabusPosition(toSyItem)
								.intValue() + 1);

						String assetStr = sd.getAsset();
						assetStr = ltiService.fixLtiLaunchUrls(assetStr, fromContext, toContext, transversalMap);
						sd.setAsset(assetStr);

						SyllabusData newToSyData = syllabusManager
								.createSyllabusDataObject(sd.getTitle(),
										positionNo, sd.getAsset(),
										sd.getView(), sd
												.getStatus(), sd
												.getEmailNotification(), sd.getStartDate(), sd.getEndDate(), sd.getLinkCalendar(),
												sd.getCalendarEventIdStartDate(), sd.getCalendarEventIdEndDate(), toSyItem);
						Set<SyllabusAttachment> attachSet = syllabusManager.getSyllabusAttachmentsForSyllabusData(sd);
						Set<SyllabusAttachment> newAttachSet = new TreeSet<SyllabusAttachment>();
						for (SyllabusAttachment thisAttach : attachSet)
						{
							ContentResource oldAttachment = contentHostingService.getResource(thisAttach.getAttachmentId());
							ContentResource attachment = contentHostingService.addAttachmentResource(
								oldAttachment.getProperties().getProperty(
										ResourceProperties.PROP_DISPLAY_NAME), 
										toContext, 
										toolManager.getTool("sakai.syllabus").getTitle(), oldAttachment.getContentType(),
										oldAttachment.getContent(), oldAttachment.getProperties());
							SyllabusAttachment thisSyllabusAttach = syllabusManager.createSyllabusAttachmentObject(
								attachment.getId(), 
								attachment.getProperties().getProperty(ResourceProperties.PROP_DISPLAY_NAME));
							thisSyllabusAttach.setSyllabusData(newToSyData);
							newAttachSet.add(thisSyllabusAttach);
						}
						newToSyData.setAttachments(newAttachSet);
						syllabusManager.addSyllabusToSyllabusItem(toSyItem,
								newToSyData, false);
				  }
				} 
				else 
				{
					log.debug("no data found for syllabusItem: {}", fromSyllabusItem.getSurrogateKey());
				}
			
			log.debug("importResources: End importing syllabus data");
		  }
		}
		catch (Exception e) 
		{
			log.error(e.getMessage(), e);
		}
		
		return transversalMap;
	}

	public void readSyllabus(SyllabusData data)
	{
    BaseResourceEdit bre = new BaseResourceEdit(data.getSyllabusId().toString(), data);
    
    addLiveSyllabusProperties(bre);
    
    bre.setEvent(EVENT_SYLLABUS_READ);
    
    int priority;
    
    priority = NotificationService.NOTI_NONE;

		Event event =
		  	eventTrackingService.newEvent(bre.getEvent(), bre.getReference(), 
			    false, priority);
		
		eventTrackingService.post(event);
	}

	public void draftChangeSyllabus(SyllabusData data)
	{
		Placement placement = toolManager.getCurrentPlacement();
		String siteId = placement.getContext();
		draftChangeSyllabus(data, siteId);
	}
    public void draftChangeSyllabus(SyllabusData data, String siteId){
    BaseResourceEdit bre = new BaseResourceEdit(data.getSyllabusId().toString(), data, siteId);
    addLiveSyllabusProperties(bre);
    
    bre.setEvent(EVENT_SYLLABUS_DRAFT_CHANGE);
    
    int priority;
    
    priority = NotificationService.NOTI_NONE;

		Event event =
		  	eventTrackingService.newEvent(bre.getEvent(), bre.getReference(), 
			    true, priority);
		
		eventTrackingService.post(event);
	}

	public void draftNewSyllabus(SyllabusData data)
	{
    BaseResourceEdit bre = new BaseResourceEdit(data.getSyllabusId().toString(), data);
    
    addLiveSyllabusProperties(bre);
    
    bre.setEvent(EVENT_SYLLABUS_DRAFT_NEW);
    
    int priority;
    
    priority = NotificationService.NOTI_NONE;

		Event event =
		  	eventTrackingService.newEvent(bre.getEvent(), bre.getReference(), 
			    true, priority);
		
		eventTrackingService.post(event);
	}
	
	//permission convert
	public String getEntityReference(SyllabusData sd, String thisSiteId)
	{
		BaseResourceEdit bre = new BaseResourceEdit(sd.getSyllabusId().toString(), sd, thisSiteId);		
		return bre.getReference();
	}
	
	//permission convert
	public String getSyllabusApplicationSiteReference(String thisSiteId)
	{
		BaseResourceEdit bre = new BaseResourceEdit(thisSiteId);		
		return bre.getReference();
	}

	private String getCurrentSiteReference() {
		//sakai2 - use Placement to get context instead of getting currentSitePageId from PortalService in sakai.
		Placement placement = toolManager.getCurrentPlacement();
		String currentSiteId = placement.getContext(); 
		return siteService.siteReference(currentSiteId);

	}
	
	public boolean checkPermission(String lock) {
			return checkPermission(lock,getCurrentSiteReference());
	}

	//permission convert
	public boolean checkPermission(String lock, String reference)
	{
		return securityService.unlock(lock, reference);
	}
	
	public boolean checkAddOrEdit() {
		return checkAddOrEdit(getCurrentSiteReference());
	}

	public boolean checkAddOrEdit(String reference) {
		return (checkPermission(SyllabusService.SECURE_ADD_ITEM,reference) ||
				checkPermission(SyllabusService.SECURE_BULK_ADD_ITEM,reference) ||
				checkPermission(SyllabusService.SECURE_BULK_EDIT_ITEM,reference));
	}
	
	public Map<String, String> transferCopyEntities(String fromContext, String toContext, List<String> ids, List<String> options, boolean cleanup)
	{	
		Map<String, String> transversalMap = new HashMap<String, String>();
		try
		{
			if (cleanup)
			{
				SyllabusItem targetSyllabusItem = syllabusManager.getSyllabusItemByContextId(toContext);
			    
				if (targetSyllabusItem != null)
				{
					// Create a copy of the targetSyllabusData set
					Set<SyllabusData> targetSyllabusDataCopy = new HashSet<>(syllabusManager.getSyllabiForSyllabusItem(targetSyllabusItem));

					for (SyllabusData sd : targetSyllabusDataCopy)
					{
						log.info("Removed SyllabusData during cleanup in transferCopyEntities: {}", sd.getSyllabusId());
						syllabusManager.removeSyllabusFromSyllabusItem(targetSyllabusItem, sd);
					}
				}
			}
		}
		catch (Exception e)
		{
			log.error("syllabus import from site [{}] to site [{}] failed, {}", fromContext, toContext, e.toString());
		}
		transversalMap.putAll(transferCopyEntities(fromContext, toContext, ids, options));
		
		return transversalMap;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void updateEntityReferences(String toContext, String fromContext, Map<String, String> transversalMap){
		if(transversalMap != null){
			Set<Entry<String, String>> entrySet = (Set<Entry<String, String>>) transversalMap.entrySet();	  	

			try
			{
				String toSiteId = toContext;

				SyllabusItem fromSyllabusItem = syllabusManager.getSyllabusItemByContextId(toSiteId);

				if (fromSyllabusItem != null) 
				{
					Set fromSyDataSet = syllabusManager.getSyllabiForSyllabusItem(fromSyllabusItem);

					Iterator fromSetIter = fromSyDataSet.iterator();

					while (fromSetIter.hasNext()) 
					{
						SyllabusData fromSyllabusData = (SyllabusData) fromSetIter.next();

						boolean updated = false;
						//Body Text
						String msgBody = fromSyllabusData.getAsset();
						if(msgBody != null){
							StringBuffer msgBodyPreMigrate = new StringBuffer(msgBody);
							msgBody = LinkMigrationHelper.migrateAllLinks(entrySet, msgBody);
							if(!msgBody.equals(msgBodyPreMigrate.toString())){
								fromSyllabusData.setAsset(msgBody);
								updated = true;
							}
						}
						//Start Date
						String startCalEventId = fromSyllabusData.getCalendarEventIdStartDate();
						if(startCalEventId != null){
							StringBuffer startCalIdMigrate = new StringBuffer(startCalEventId);
							startCalEventId = LinkMigrationHelper.migrateAllLinks(entrySet, startCalEventId);
							if(!startCalEventId.equals(startCalIdMigrate.toString())){
								fromSyllabusData.setCalendarEventIdStartDate(startCalEventId);
								updated = true;
							}else{
								//we couldn't find the calendar event tied to this item,
								//this means it wasn't imported over
								//we need to remove it and update the data properties
								fromSyllabusData.setCalendarEventIdStartDate(null);
								if(fromSyllabusData.getCalendarEventIdEndDate() == null){
									//unlink the calendar if end date ID is null
									//otherwise, just let end date id logic determine
									//whether to set link to false
									fromSyllabusData.setLinkCalendar(false);
								}
								updated = true;
							}
						}
						//End Date
						String endCalEventId = fromSyllabusData.getCalendarEventIdEndDate();
						if(endCalEventId != null){
							StringBuffer endCalIdMigrate = new StringBuffer(endCalEventId);
							endCalEventId = LinkMigrationHelper.migrateAllLinks(entrySet, endCalEventId);
							if(!endCalEventId.equals(endCalIdMigrate.toString())){
								fromSyllabusData.setCalendarEventIdEndDate(endCalEventId);
								updated = true;
							}else{
								//we couldn't find the calendar event tied to this item,
								//this means it wasn't imported over
								//we need to remove it and update the data properties
								fromSyllabusData.setCalendarEventIdEndDate(null);
								if(fromSyllabusData.getCalendarEventIdStartDate() == null){
									//both end and start IDs are null, uncheck the link for calendar
									fromSyllabusData.setLinkCalendar(false);
								}
								updated = true;
							}
						}
						
						if(updated){
							syllabusManager.saveSyllabus(fromSyllabusData);
						}
					}
				}
			}
			catch (Exception e)
			{
				log.debug("Syllabus updateEntityReferences failed" + e);
			}
		}
	}
	
}
