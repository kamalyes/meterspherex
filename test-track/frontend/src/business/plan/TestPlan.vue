<template>
  <ms-container>

    <ms-main-container>
      <test-plan-list
        @openTestPlanEditDialog="openTestPlanEditDialog"
        @testPlanEdit="openTestPlanEditDialog"
        ref="testPlanList"/>

    </ms-main-container>

    <test-plan-edit ref="testPlanEditDialog" @refresh="refreshTestPlanList"/>

  </ms-container>
</template>

<script>

import TestPlanList from './components/TestPlanList';
import TestPlanEdit from './components/TestPlanEdit';
import MsContainer from "metersphere-frontend/src/components/MsContainer";
import MsMainContainer from "metersphere-frontend/src/components/MsMainContainer";
import {getCurrentProjectID} from "metersphere-frontend/src/utils/token";

export default {
  name: "TestPlan",
  components: {MsMainContainer, MsContainer, TestPlanList, TestPlanEdit},
  data() {
    return {};
  },
  computed: {
    projectId() {
      return getCurrentProjectID();
    },
  },
  mounted() {
    if (this.$route.path.indexOf("/track/plan/create") >= 0) {
      this.openTestPlanEditDialog();
      this.$router.push('/track/plan/all');
    }
  },
  watch: {
    '$route'(to, from) {
      if (to.path.indexOf("/track/plan/create") >= 0) {
        if (!this.projectId) {
          this.$warning(this.$t('commons.check_project_tip'));
          return;
        }
        this.openTestPlanEditDialog();
        this.$router.push('/track/plan/all');
      }
    }
  },
  methods: {
    openTestPlanEditDialog(data) {
      this.$refs.testPlanEditDialog.openTestPlanEditDialog(data);
    },
    refreshTestPlanList() {
      this.$refs.testPlanList.initTableData();
    }
  }
};
</script>

<style scoped>

</style>
