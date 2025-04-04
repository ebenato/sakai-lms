/**
 * Copyright (c) 2003-2017 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.gradebookng.tool.panels;

import java.io.Serializable;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.validation.validator.StringValidator;

import org.sakaiproject.gradebookng.business.importExport.CommentValidator;
import org.sakaiproject.gradebookng.business.model.GbUser;
import org.sakaiproject.gradebookng.tool.component.GbAjaxButton;
import org.sakaiproject.gradebookng.tool.component.GbFeedbackPanel;
import org.sakaiproject.grading.api.Assignment;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * Panel for the modal window that allows an instructor to set/update a comment for a grade
 *
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 *
 */
public class EditGradeCommentPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final ModalWindow window;
    /**
     * -- GETTER --
     *  Getter for the comment string so we can update components on the parent page when the comment is saved here
     *
     * @return
     */
    @Getter
    private String comment;

	public EditGradeCommentPanel(final String id, final IModel<Map<String, Object>> model, final ModalWindow window) {
		super(id, model);
		this.window = window;
	}

	@Override
	public void onInitialize() {
		super.onInitialize();

		// unpack model
		final Map<String, Object> modelData = (Map<String, Object>) getDefaultModelObject();
		final Long assignmentId = (Long) modelData.get("assignmentId");
		final String studentUuid = (String) modelData.get("studentUuid");

		// fetch current comment
		this.comment = this.businessService.getAssignmentGradeComment(currentGradebookUid, assignmentId, studentUuid);

		// form model
		final GradeComment gradeComment = new GradeComment();
		gradeComment.setGradeComment(this.comment);
		final CompoundPropertyModel<GradeComment> formModel = new CompoundPropertyModel<>(gradeComment);

		// build form
		// modal window forms must be submitted via AJAX so we do not specify an onSubmit here
		final Form<GradeComment> form = new Form<>("form", formModel);

		final GbAjaxButton submit = new GbAjaxButton("submit") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit(final AjaxRequestTarget target) {

				final GradeComment updatedComment = (GradeComment) form.getModelObject();

				final boolean success = EditGradeCommentPanel.this.businessService.updateAssignmentGradeComment(currentGradebookUid, currentSiteId, assignmentId, studentUuid,
						updatedComment.getGradeComment());

				if (success) {
					// update member var
					EditGradeCommentPanel.this.comment = updatedComment.getGradeComment();

					// store the instructor feedback comment
					target.appendJavaScript(String.format("GbGradeTable.saveNewPrediction(\"%s\");", StringEscapeUtils.escapeJson(updatedComment.getGradeComment())));

					// trigger a close
					EditGradeCommentPanel.this.window.close(target);
				} else {

					// TODO need to handle the error here
				}
			}

			@Override
			protected void onError(final AjaxRequestTarget target) {
				target.addChildren(form, FeedbackPanel.class);
			}

		};
		form.add(submit);

		final GbAjaxButton cancel = new GbAjaxButton("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit(final AjaxRequestTarget target) {
				EditGradeCommentPanel.this.window.close(target);
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

		// heading
		// TODO if user/assignment has been deleted since rendering the GradebookPage, handle nulls here gracefully
		final GbUser user = this.businessService.getUser(studentUuid);
		final Assignment assignment = this.businessService.getAssignment(currentGradebookUid, currentSiteId, assignmentId);
		EditGradeCommentPanel.this.window.setTitle(
				(new StringResourceModel("heading.editcomment")
						.setParameters(user.getDisplayName(), user.getDisplayId(), assignment.getName())).getString())
				.setEscapeModelStrings(false);

		// textarea
		form.add(new TextArea<>("comment", new PropertyModel<>(formModel, "gradeComment"))
				.add(StringValidator.maximumLength(CommentValidator.getMaxCommentLength(serverConfigService))));

		// instant validation
		// AjaxFormValidatingBehavior.addToAllFormComponents(form, "keyup", Duration.ONE_SECOND);

		// feedback panel
		form.add(new GbFeedbackPanel("editCommentFeedback"));

		add(form);
	}

    /**
	 * Model for this form
	 */
	class GradeComment implements Serializable {

		private static final long serialVersionUID = 1L;

		@Getter
		@Setter
		private String gradeComment;

	}

}
