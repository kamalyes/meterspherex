<template>
  <div>
    <el-form-item :label-width="labelWidth" :label="$t('project.jira_key')">
      <el-input v-model="form.jiraKey" autocomplete="off" @blur="getIssueTypeOption"/>
      <slot name="checkBtn"></slot>
      <ms-instructions-icon effect="light">
        <template>
          <img class="jira-image" src="assets/jira-key.png"/>
        </template>
      </ms-instructions-icon>
    </el-form-item>
    <el-form-item :label-width="labelWidth" :label="$t('organization.integration.jira_issuetype')" prop="issuetype">
      <el-select filterable v-model="form.issueConfigObj.jiraIssueTypeId">
        <el-option v-for="item in issueTypes" :key="item.id" :label="item.name" :value="item.id">
        </el-option>
      </el-select>
    </el-form-item>
    <el-form-item :label-width="labelWidth" :label="$t('organization.integration.jira_storytype')" prop="storytype">
      <el-select filterable v-model="form.issueConfigObj.jiraStoryTypeId">
        <el-option v-for="item in issueTypes" :key="item.id" :label="item.name" :value="item.id">
        </el-option>
      </el-select>
    </el-form-item>
  </div>
</template>

<script>
import MsInstructionsIcon from "metersphere-frontend/src/components/MsInstructionsIcon";
import {getJiraIssueType} from "../../../api/project";
import {getCurrentWorkspaceId} from "metersphere-frontend/src/utils/token";

export default {
  name: "ProjectJiraConfig",
  components: {MsInstructionsIcon},
  props: ['labelWidth', 'form', 'result'],
  data() {
    return {
      issueTypes: []
    }
  },
  mounted() {
    this.getIssueTypeOption();
  },
  methods: {
    getIssueTypeOption() {
      this.issueTypes = [];
      this.result.loading = true;
      let param = {
        projectId: this.form.id,
        workspaceId: getCurrentWorkspaceId(),
        jiraKey: this.form.jiraKey
      }
      getJiraIssueType(param)
        .then((response) => {
          this.issueTypes = response.data;
          let hasJiraIssueType = false;
          let hasJiraStoryType = false;
          if (response.data) {
            response.data.forEach(item => {
              if (this.form.issueConfigObj.jiraIssueTypeId === item.id) {
                hasJiraIssueType = true;
              } else if (this.form.issueConfigObj.jiraStoryTypeId === item.id) {
                hasJiraStoryType = true;
              }
            });
          }
          if (!hasJiraIssueType) {
            this.form.issueConfigObj.jiraIssueTypeId = null;
          }
          if (!hasJiraStoryType) {
            this.form.issueConfigObj.jiraStoryTypeId = null;
          }
          this.result.loading = false;
        });
    }
  }
}
</script>

<style scoped>
.el-input, .el-textarea {
  width: 80%;
}
</style>
