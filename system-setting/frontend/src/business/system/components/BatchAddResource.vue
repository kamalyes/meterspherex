<template>
  <div>
    <ms-drawer :visible="dialogVisible" :size="30" @close="handleClose" direction="right"
               :show-full-screen="false" :is-show-close="false">
      <div>
        <el-row>
          <el-col :span="14">
            <div v-html="$t('test_resource_pool.batch_add_resource_tips')"></div>
          </el-col>
          <el-col :span="10" class="buttons">
            <el-button size="mini" @click="handleClose">{{ $t('commons.cancel') }}</el-button>
            <el-button type="primary" size="mini" @click="confirm" @keydown.enter.native.prevent>
              {{ $t('commons.confirm') }}
            </el-button>
          </el-col>
        </el-row>
        <div class="ms-code">
          <ms-code-edit class="ms-code" :enable-format="false" mode="text" :data.sync="parameters" theme="eclipse"
                        :modes="['text']"
                        ref="codeEdit"/>
        </div>
      </div>
    </ms-drawer>
  </div>
</template>

<script>
import MsDialogFooter from "metersphere-frontend/src/components/MsDialogFooter";
import {listenGoBack, removeGoBackListener} from "metersphere-frontend/src/utils";
import MsCodeEdit from "metersphere-frontend/src/components/MsCodeEdit";
import MsDrawer from "metersphere-frontend/src/components/MsDrawer";

export default {
  name: "BatchAddResource",
  components: {
    MsDrawer,
    MsDialogFooter,
    MsCodeEdit
  },
  props: {},
  data() {
    return {
      dialogVisible: false,
      parameters: "",
    };
  },
  methods: {
    open() {
      this.dialogVisible = true;
      listenGoBack(this.handleClose);
    },
    handleClose() {
      this.parameters = "";
      this.dialogVisible = false;
      removeGoBackListener(this.handleClose);
    },
    confirm() {
      this.dialogVisible = false;
      this.$emit("batchSave", this.parameters);
    }
  }
};
</script>

<style scoped>

.ms-drawer {
  padding: 10px 13px;
}

.ms-code {
  height: calc(100vh);
}

.buttons .el-button {
  float: right;
}

.buttons .el-button:nth-child(2) {
  margin-right: 15px;
}
</style>
