<template>
  <div class="request-result">
    <div>
      <el-row :gutter="8" type="flex" align="middle" class="info">
        <el-col :span="2">
          <div class="method">
            {{request.method}}
          </div>
        </el-col>
        <el-col :span="8">
          <el-tooltip effect="dark" :content="request.url" placement="bottom" :open-delay="800">
            <div class="url">{{request.url}}</div>
          </el-tooltip>
        </el-col>
        <el-col :span="8">
          <div class="url"> {{$t('api_report.start_time')}}：{{request.startTime | timestampFormatDate(true) }}
          </div>
        </el-col>
      </el-row>
    </div>
    <el-collapse-transition>
      <div v-show="isActive">
        <ms-response-text :request-type="requestType" v-if="isCodeEditAlive" :response="request.responseResult" :request="request"/>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script>
  import MsResponseText from "./ResponseText";

  export default {
    name: "MsRequestSubResultTail",
    components: {MsResponseText},
    props: {
      request: Object,
      scenarioName: String,
      requestType: String
    },

    data() {
      return {
        isActive: true,
        activeName: "sub",
        isCodeEditAlive: true
      }
    },

    methods: {
      active() {
        this.isActive = !this.isActive;
      },
      reload() {
        this.isCodeEditAlive = false;
        this.$nextTick(() => (this.isCodeEditAlive = true));
      }
    },
    watch: {
      'request.responseResult'() {
        this.reload();
      }
    },

    computed: {
      assertion() {
        return this.request.passAssertions + " / " + this.request.totalAssertions;
      },
      hasSub() {
        return this.request.subRequestResults.length > 0;
      },
    }
  }
</script>

<style scoped>
  .request-result {
    width: 100%;
    min-height: 40px;
    padding: 2px 0;
  }

  .request-result .info {
    background-color: #F9F9F9;
    margin-left: 20px;
    cursor: pointer;
  }

  .request-result .method {
    color: #1E90FF;
    font-size: 14px;
    font-weight: 500;
    line-height: 40px;
    padding-left: 5px;
  }

  .request-result .url {
    color: #7f7f7f;
    font-size: 12px;
    font-weight: 400;
    margin-top: 4px;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    word-break: break-all;
  }

  .request-result .tab .el-tabs__header {
    margin: 0;
  }

  .request-result .text {
    height: 300px;
    overflow-y: auto;
  }

  .sub-result .info {
    background-color: #FFF;
  }

  .sub-result .method {
    border-left: 5px solid #1E90FF;
    padding-left: 20px;
  }

  .sub-result:last-child {
    border-bottom: 1px solid #EBEEF5;
  }

  .request-result .icon.is-active {
    transform: rotate(90deg);
  }

</style>
