<template>
  <load-case-report-view :is-plan-report="true" :share-id="shareId" :is-share="isShare" :report-id="reportId"
                         ref="loadCaseReportView"/>
</template>

<script>
import {getShareId, getShareInfo} from "@/api/share";
import LoadCaseReportView from "./LoadCaseReportView";

export default {
  name: "SharePerformanceReportTemplate",
  components: {LoadCaseReportView},
  data() {
    return {
      reportId: '',
      shareId: '',
      isShare: true,
    };
  },
  created() {
    this.shareId = getShareId();
    getShareInfo(this.shareId)
      .then(({data}) => {
        if (!data) {
          this.$error('连接已失效，请重新获取!');
          return;
        }
        if (data.lang) {
          this.$setLang(data.lang);
        }
        if (data.shareType === 'PERFORMANCE_REPORT') {
          this.reportId = data.customData;
        }
      });
  },
};
</script>

<style scoped>
</style>
