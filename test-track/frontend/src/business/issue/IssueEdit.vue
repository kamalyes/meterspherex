<template>

  <el-drawer
    class="field-template-edit"
    :before-close="handleClose"
    :visible.sync="visible"
    :with-header="false"
    size="100%"
    :modal-append-to-body="false"
    ref="drawer"
  >
    <template>
      <template-component-edit-header :show-edit="false" :template="{}" prop="title" @cancel="handleClose" @save="save"/>
      <issue-edit-detail @refresh="refresh" @close="handleClose" ref="issueEditDetail"/>
    </template>
  </el-drawer>
</template>
<script>

import TemplateComponentEditHeader from "@/business/plan/view/comonents/report/TemplateComponentEditHeader";
import IssueEditDetail from "@/business/issue/IssueEditDetail";
export default {
  name: "IssueEdit",
  components: {IssueEditDetail, TemplateComponentEditHeader},
  data() {
    return {
      visible: false
    }
  },
  methods: {
    open(data, type) {
      this.visible = true;
      this.$nextTick(() => {
        this.$refs.issueEditDetail.open(data, type);
      });
    },
    handleClose() {
      this.visible = false;
      this.$refs.issueEditDetail.resetForm();
    },
    save() {
      this.$refs.issueEditDetail.save();
    },
    refresh(data) {
      this.$emit('refresh', data);
    }
  }
};
</script>

<style scoped>

</style>
