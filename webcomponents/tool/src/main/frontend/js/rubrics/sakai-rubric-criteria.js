import { RubricsElement } from "./rubrics-element.js";
import { html } from "/webcomponents/assets/lit-element/lit-element.js";
import { unsafeHTML } from "/webcomponents/assets/lit-html/directives/unsafe-html.js";
import { repeat } from "/webcomponents/assets/lit-html/directives/repeat.js";
import "./sakai-rubric-edit.js";
import "./sakai-item-delete.js";
import "./sakai-rubric-criterion-edit.js";
import "./sakai-rubric-criterion-rating-edit.js";
import { SharingChangeEvent } from "./sharing-change-event.js";
import { tr } from "./sakai-rubrics-language.js";
import "../sakai-reorderer.js";

export class SakaiRubricCriteria extends RubricsElement {

  static get properties() {

    return {
      rubricId: { attribute: "rubric-id", type: String },
      siteId: { attribute: "site-id", type: String },
      criteria: { type: Array },
      weighted: { type: Boolean },
      totalWeight: { attribute: "total-weight", type: String },
      validWeight: { attribute: "valid-weight", type: Boolean },
      maxPoints: { attribute: "max-points", type: String },
      minPoints: { attribute: "min-points", type: String },
      isLocked: { attribute: "is-locked", type: Boolean },
      isDraft: { attribute: "is-draft", type: Boolean },
    };
  }

  updated(changedProperties) {

    super.updated(changedProperties);

    if (changedProperties.has("criteria")) {
      this.criteriaMap = new Map(this.criteria.map(c => [c.id, c]));
    }
  }

  renderAddRatingButton(c, pos = 0) {

    return html`
      <button data-criterion-id="${c.id}"
          aria-label="${tr("add_rating")} ${c.title}"
          title="${tr("add_rating")} ${c.title}"
          @click=${this.addRating}
          data-rating-pos="${pos}">
        <span class="fa fa-plus"></span>
      </button>
    `;
  }

  _criteriaReordered(e) {

    this.criteria = e.detail.reorderedIds.map(id => this.criteriaMap.get(parseInt(id)));

    // Focus the moved criterion's drag handle
    this.updateComplete.then(() => {

      this.querySelectorAll("sakai-reorderer").forEach(el => el.requestUpdate());
      this.querySelector(`[data-criterion-id="${e.detail.data.criterionId}"] .drag-handle`).focus();
    });

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/sort`;
    fetch(url, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(e.detail.reorderedIds),
    })
    .then(r => {

      if (!r.ok) {
        throw new Error("Network error while saving criteria sort");
      }
    })
    .catch (error => console.error(error));
  }

  _ratingsReordered(e) {

    e.stopPropagation();

    const criterionId = e.detail.data.criterionId;
    const criterion = this.criteria.find(c => c.id == criterionId);

    // Reorder the ratings based on the sort result
    criterion.ratings = e.detail.reorderedIds.map(id => criterion.ratings.find(r => r.id == id));
    this.requestUpdate();

    // Focus the moved rating's drag handle
    this.updateComplete.then(() => {
      this.querySelector(`[data-rating-id="${e.detail.data.ratingId}"] .drag-handle`).focus();
    });

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/${criterionId}/ratings/sort`;
    fetch(url, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(e.detail.reorderedIds),
    })
    .then(r => {

      if (!r.ok) {
        throw new Error(`Network error while saving ratings sort at ${url}`);
      }

    })
    .catch (error => console.error(error));
  }

  render() {

    return html`
      <sakai-reorderer drop-class="criterion-row" @reordered=${this._criteriaReordered}>
        <div data-rubric-id="${this.rubricId}" class="criterion style-scope sakai-rubric-criterion">
        ${repeat(this.criteria, c => c.id, c => html`
          ${this.isCriterionGroup(c) ? html`
            <div id="criterion_row_${c.id}" data-criterion-id="${c.id}" data-reorderable-id="${c.id}" class="criterion-row criterion-group">
              <div class="criterion-detail criterion-title">
                <h4 class="criterion-title d-flex align-items-center">
                  <div>
                    <span tabindex="0"
                        title="${tr("drag_order")}"
                        data-criterion-id="${c.id}"
                        aria-label="${tr("drag_to_reorder_label")}"
                        class="drag-handle reorder-icon si si-drag-handle fs-3">
                    </span>
                  </div>
                  <div class="ms-1">${c.title}</div>
                  <div>
                    <sakai-rubric-criterion-edit
                        id="criterion-edit-${c.id}"
                        @criterion-edited="${this.criterionEdited}"
                        site-id="${this.siteId}"
                        rubric-id="${this.rubricId}"
                        criterion="${JSON.stringify(c)}"
                        ?is-criterion-group="${true}">
                    </sakai-rubric-criterion-edit>
                  </div>
                </h4>
                <p>${unsafeHTML(c.description)}</p>
              </div>
              <div class="criterion-actions">
                ${!this.isLocked ? html`
                  <a tabindex="0" role="button" data-criterion-id="${c.id}" title="${tr("copy")} ${c.title}" aria-label="${tr("copy")} ${c.title}" class="linkStyle clone fa fa-copy" @click="${this.cloneCriterion}" href="#"></a>
                  <sakai-item-delete criterion-id="${c.id}" site-id="${this.siteId}" criterion="${JSON.stringify(c)}" rubric-id="${this.rubricId}" @delete-item="${this.deleteCriterion}" token="${this.token}"></sakai-item-delete>`
                  : ""
                }
              </div>
            </div>
          ` : html`
            <div id="criterion_row_${c.id}" data-criterion-id="${c.id}" data-reorderable-id="${c.id}" class="criterion-row">
              <div class="criterion-detail">
                <h4 class="criterion-title d-flex align-items-center">
                  <div>
                    <span tabindex="0"
                        title="${tr("drag_order")}"
                        data-criterion-id="${c.id}"
                        aria-label="${tr("drag_to_reorder_label")}"
                        class="drag-handle reorder-icon si si-drag-handle fs-3">
                    </span>
                  </div>
                  <div class="ms-1">${c.title}</div>
                  <div>
                    <sakai-rubric-criterion-edit
                        id="criterion-edit-${c.id}"
                        @criterion-edited="${this.criterionEdited}"
                        site-id="${this.siteId}"
                        rubric-id="${this.rubricId}"
                        criterion="${JSON.stringify(c)}">
                    </sakai-rubric-criterion-edit>
                  </div>
                </h4>
                <p>
                  ${unsafeHTML(c.description)}
                </p>
                ${this.weighted ? html`
                    <div class="weight-field">
                      ${!this.isLocked ? html`
                        <div class="field-item form-group input-group-sm ${this.validWeight ? "" : "weight-error"}">
                          <label
                            for="weight_input_${c.id}"
                            class="form-control-label"
                            title="${!this.validWeight ? tr("total_weight_wrong") : ""}"
                          >
                            <sr-lang key="weight">Weight</sr-lang>
                          </label>
                          <input
                            id="weight_input_${c.id}"
                            data-criterion-id="${c.id}"
                            type="text"
                            class="form-control"
                            placeholder="0.0"
                            @input="${this.debounce(this.emitWeightChanged, 500)}"
                            value="${c.weight.toLocaleString(this.locale)}"
                            title="${!this.validWeight ? tr("total_weight_wrong") : ""}"
                          >
                          <span class="form-control-label"
                            title="${!this.validWeight ? tr("total_weight_wrong") : ""}"
                          >
                            <sr-lang key="percent_sign">%</sr-lang>
                          </span>
                        </div>`
                        : ""
                      }
                      <div class="field-item">
                        <span>${tr('min_max_points', [this.getCriterionMinPoints(c.id), this.getCriterionMaxPoints(c.id)])}</span>
                      </div>
                    </div>`
                    : ""
                  }
                ${!this.isLocked ? html`
                  <div class="add-criterion-item">
                    ${this.renderAddRatingButton(c)}
                  </div>`
                  : ""
                }
              </div>
              <div class="criterion-ratings">
                <sakai-reorderer id="criterion-ratings-reorderer-${c.id}" class="rating-reorderer" @reordered=${this._ratingsReordered} horizontal>
                  <div id="cr-table-${c.id}" class="cr-table" data-criterion-id="${c.id}">
                  ${repeat(c.ratings, r => r.id, (r, i) => html`
                    <div class="rating-item"
                        data-criterion-id="${c.id}"
                        data-rating-id="${r.id}"
                        data-reorderable-id="${r.id}"
                        id="rating_item_${r.id}">
                      <h5 class="criterion-item-title">
                        ${r.title}
                        <sakai-rubric-criterion-rating-edit
                          criterion-id="${c.id}"
                          @save-rating="${this.saveRating}"
                          @delete-rating="${this.deleteRating}"
                          minpoints="${c.pointrange ? c.pointrange.low : 0}"
                          maxpoints="${c.pointrange ? c.pointrange.high : 0}"
                          rating="${JSON.stringify(r)}"
                          ?removable="${ this.isRatingRemovable(c) }"
                          ?is-locked="${this.isLocked}">
                        </sakai-rubric-criterion-rating-edit>
                      </h5>
                      <div class="div-description">
                        <p>
                        ${r.description}
                        </p>
                      </div>
                      <span class="points">
                        ${this.weighted && r.points > 0 ? html`
                          <b>
                            (${parseFloat((r.points * (c.weight / 100)).toFixed(2)).toLocaleString(this.locale)})
                          </b>`
                          : ""
                        }
                        ${parseFloat(r.points).toLocaleString(this.locale)} <sr-lang key="points">Points</sr-lang>
                      </span>
                      ${!this.isLocked ? html`
                        <div class="add-criterion-item">
                          ${this.renderAddRatingButton(c, i + 1)}
                        </div>
                        <span tabindex="0"
                            data-criterion-id="${c.id}"
                            data-rating-id="${r.id}"
                            title="${tr("drag_order")}"
                            aria-label="${tr("drag_to_reorder_label")}"
                            aria-describedby="rubrics-reorder-info"
                            class="drag-handle reorder-icon sideways si si-drag-handle">
                        </span>`
                        : ""
                      }
                    </div>
                  `)}
                  </div>
                </sakai-reorderer>
              </div>
              ${!this.isLocked ? html`
                <div class="criterion-actions">
                  <a tabindex="0" role="button" data-criterion-id="${c.id}" title="${tr("copy")} ${c.title}" aria-label="${tr("copy")} ${c.title}" class="linkStyle clone fa fa-copy" @keyup="${this.openEditWithKeyboard}" @click="${this.cloneCriterion}" href="#"></a>
                  <sakai-item-delete criterion-id="${c.id}" site-id="${this.siteId}" criterion="${JSON.stringify(c)}" rubric-id="${this.rubricId}" @delete-item="${this.deleteCriterion}"></sakai-item-delete>
                </div>`
                : ""
              }
            </div>
          `}
        `)}
        </div>
      </sakai-reorderer>
      ${this.weighted ? html`
        <div class="weighted-grade-info">
          <div class="total-data">
            <div class="${this.validWeight ? "" : "weight-error"}">
              <span class="form-control-label">
                <span class="bold-header">${tr("total_weight")}</span>
                <span>${this.totalWeight}<sr-lang key="percent_sign">%</sr-lang></span>
              </span>
            </div>
            <div>
              <span class="bold-header">${tr("total_grade")}</span>
              <span>${tr('min_max_points', [this.minPoints, this.maxPoints])}</span>
            </div>
          </div>
          <div class="banner-container">
            <div class="sak-banner-success d-none ">
              <sr-lang key="saved_successfully">%</sr-lang>
            </div>
            <div class="sak-banner-warn ${!this.validWeight && this.isDraft ? "" : "d-none"}">
            <sr-lang key="draft_save_invalid_weights">%</sr-lang>
            </div>
            <div class="sak-banner-error ${!this.validWeight && !this.isDraft ? "" : "d-none"}">
              <sr-lang key="total_weight_wrong">%</sr-lang>
            </div>
          </div>
        </div>`
        : ""
      }
      ${!this.isLocked ? html`
        <div class="action-butons">
          ${this.weighted ? html`
            <button class="btn-link save-weights" @click="${this.saveWeights}" ?disabled="${!this.validWeight && !this.isDraft}">
              <span class="add fa fa-save"></span>
              <sr-lang key="save_weights">Save Weights</sr-lang>
            </button>`
            : ""
          }
          <button class="btn-link add-criterion" @click="${this.createCriterion}">
            <span class="add fa fa-plus"></span>
            <sr-lang key="add_criterion">Add Criterion</sr-lang>
          </button>
          <button class="btn-link add-empty-criterion" @click="${(event) => this.createCriterion(event, true)}">
            <span class="add fa fa-plus"></span>
            <sr-lang key="add_criterion_group">Add Criterion Group</sr-lang>
          </button>
        </div>
        ${this.isDraft ? html`
        <div class="sak-banner-warn margin-bottom">
          <sr-lang key="draft_info">%</sr-lang>
        </div>`
          : ""
        }
      ` : html`
        <div class="sak-banner-warn margin-bottom">
          <sr-lang key="locked_warning">%</sr-lang>
        </div>`
      }
      <br>
    `;
  }

  letShareKnow() {
    this.dispatchEvent(new SharingChangeEvent());
  }

  _handleSortedRatings(e) {

    e && e.stopPropagation();

    const criterionId = e.target.dataset.criterionId;
    const criterion = this.criteria.find(c => c.id == criterionId);

    const sortedIds = Array.from(this.querySelectorAll(`#cr-table-${criterionId} .rating-item`)).map(r => r.dataset.ratingId);

    // Reorder the ratings based on the sort result
    criterion.ratings = sortedIds.map(id => criterion.ratings.find(r => r.id == id));

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/${criterionId}/ratings/sort`;
    fetch(url, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(sortedIds),
    })
    .then(r => {

      if (!r.ok) {
        throw new Error(`Network error while saving ratings sort at ${url}`);
      }
    })
    .catch (error => console.error(error));
  }

  saveRating(e) {

    e.stopPropagation();

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/${e.detail.criterionId}/ratings/${e.detail.rating.id}`;
    fetch(url, {
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      method: "POST",
      body: JSON.stringify(e.detail.rating)
    })
    .then(r => {

      if (r.ok) {
        const criterion = this.criteria.find(c => c.id == e.detail.criterionId);
        const rating = criterion.ratings.find(rat => rat.id == e.detail.rating.id);
        rating.title = e.detail.rating.title;
        rating.description = e.detail.rating.description;
        rating.points = e.detail.rating.points;
        rating.new = false;
        this.requestUpdate();
        this.updateComplete.then(() => this.querySelector(`#criterion-ratings-reorderer-${e.detail.criterionId}`).requestUpdate());
        this.letShareKnow();
        this.dispatchEvent(new CustomEvent('refresh-total-weight', { detail: { criteria: this.criteria } }));
      } else {
        throw new Error("Network error while saving rating");
      }
    })
    .catch(error => console.error(error));
  }

  deleteCriterion(e) {

    e.stopPropagation();
    const index = this.criteria.findIndex(c => c.id === e.detail.id);
    this.criteria.splice(index, 1);
    this.criteriaMap.delete(e.detail.id);
    this.requestUpdate();
    this.updateComplete.then(() => this.querySelector("sakai-reorderer").requestUpdate());
    this.letShareKnow();
    this.dispatchEvent(new CustomEvent('refresh-total-weight', { detail: { criteria: this.criteria } }));
  }

  emitWeightChanged(e) {

    if (e.target.value == '') {
      e.target.value = 0;
    }
    let value = e.target.value.replace(',', '.');
    value = parseFloat(value);
    if (isNaN(value)) {
      value = 0;
    }
    const id = parseInt(e.target.getAttribute('data-criterion-id'));
    if (isNaN(id)) {
      return;
    }
    this.letShareKnow();
    this.dispatchEvent(new CustomEvent('weight-changed', { detail: { criterionId: id, value, criteria: this.criteria } }));
    this.requestUpdate();
  }

  addRating(e) {

    const criterionId = e.target.dataset.criterionId;
    const ratingPos = e.target.dataset.ratingPos;

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/${criterionId}/ratings/default?position=${ratingPos}`;
    fetch(url, {
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    })
    .then(r => {

      if (r.ok) {
        return r.json();
      }
      throw new Error("Network error while adding rating");
    })
    .then(nr => this.addRatingResponse(criterionId, ratingPos, nr))
    .catch (error => console.error(error));
  }

  addRatingResponse(criterionId, ratingPos, newRating) {

    newRating.new = true;

    const criterion = this.criteriaMap.get(parseInt(criterionId));

    if (!criterion.ratings) criterion.ratings = [];

    criterion.ratings.splice(parseInt(ratingPos), 0, newRating);

    this.letShareKnow();
    this.requestUpdate();
  }

  deleteRating(e) {

    e.stopPropagation();

    const criterion = this.criteriaMap.get(parseInt(e.detail.criterionId));

    if (!criterion) {
      console.error(`No criterion found with id ${e.detail.criterionId}`);
      return;
    }

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/${e.detail.criterionId}/ratings/${e.detail.id}`;
    fetch(url, {
      method: "DELETE",
      credentials: "include",
    })
    .then(r => {

      if (r.ok) {
        return r.json();
      }

      throw new Error("Network error while deleting rating");
    })
    .then(c => {

      // The current weight may be lost because comes from DB and the UI can have a different value.
      c.weight = criterion.weight;

      Object.assign(criterion, c);
      this.requestUpdate();
      this.updateComplete.then(() => this.querySelector(`#criterion-ratings-reorderer-${e.detail.criterionId}`).requestUpdate());
      this.letShareKnow();
      this.dispatchEvent(new CustomEvent('refresh-total-weight', { detail: { criteria: this.criteria } }));
    })
    .catch (error => console.error(error));
  }

  // SAK-47640 - Get the maximum and minimum possible grade of the criterion,
  // multiplying the max-min rating points of the criterion by the criterion weight
  getCriterionMaxPoints(criterionId) {
    return this.getCriterionPoints(criterionId, Math.max);
  }

  getCriterionMinPoints(criterionId) {
    return this.getCriterionPoints(criterionId, Math.min);
  }

  getCriterionPoints(criterionId, minOrMax) {

    let totalPoints = 0;
    const criterion = this.criteria.find(c => c.id == criterionId);

    totalPoints += minOrMax(...criterion.ratings.map( (rating) => {
      return rating.points * (criterion.weight / 100);
    }));

    return parseFloat(totalPoints).toLocaleString(this.locale);
  }

  cloneCriterion(e) {

    e.preventDefault();
    e.stopPropagation();

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/${e.target.dataset.criterionId}/copy`;
    fetch(url, {
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    })
    .then(r => {

      if (r.ok) {
        return r.json();
      }
      throw new Error("Server error while cloning criterion");
    })
    .then(clone => this.createCriterionResponse(clone))
    .catch(error => console.error(error));
  }

  createCriterionResponse(nc) {

    this.criteria.push(nc);
    this.criteriaMap.set(nc.id, nc);

    // Add the criterion to the rubric
    this.requestUpdate();
    this.updateComplete.then(() => this.querySelector("sakai-reorderer").requestUpdate());
  }

  criterionEdited(e) {

    const criterion = this.criteriaMap.get(e.detail.id);
    criterion.title = e.detail.title;
    criterion.description = e.detail.description;
    criterion.isNew = e.detail.isNew;
    this.requestUpdate();

    this.querySelector(`sakai-item-delete[criterion-id="${e.detail.id}"]`).requestUpdate("criterion", criterion);
  }

  saveWeights() {
    this.dispatchEvent(new CustomEvent('save-weights'));
  }

  createCriterion(e, empty = false) {

    const url = `/api/sites/${this.siteId}/rubrics/${this.rubricId}/criteria/default${empty ? "Empty" : ""}`;
    fetch(url, {
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    })
    .then(r => {

      if (r.ok) {
        return r.json();
      }
      throw new Error("Network error while getting default criterion");
    })
    .then(nc => {
      this.createCriterionResponse(nc);
    })
    .catch(error => console.error(error));
  }

  openEditWithKeyboard(e) {

    if (e.keyCode == 32) {
      this.cloneCriterion(e);
    }
  }

  isRatingRemovable(criterion) {
    return criterion.ratings.length > 1;
  }

  debounce(fn, delay) {

    let timer = null;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => {
        fn.apply(this, args);
      }, delay);
    };
  }
}

const tagName = "sakai-rubric-criteria";
!customElements.get(tagName) && customElements.define(tagName, SakaiRubricCriteria);
