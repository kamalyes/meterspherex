<template>

  <el-dialog :title="$t('test_track.plan_view.change_executor')"
             :visible.sync="executorEditVisible"
             width="20%">
    <el-select v-model="executor" :placeholder="$t('test_track.plan_view.select_executor')">
      <el-option v-for="item in executorOptions" :key="item.id"
                 :label="item.name" :value="item.id"></el-option>
    </el-select>

    <template v-slot:footer>
      <ms-dialog-footer
        @cancel="executorEditVisible = false"
        @confirm="saveExecutor"/>
    </template>
  </el-dialog>

</template>

<script>
import MsDialogFooter from 'metersphere-frontend/src/components/MsDialogFooter'

export default {
      name: "executorEdit",
      components: {MsDialogFooter},
      data() {
        return {
          executorEditVisible: false,
          executor: '',
          executorOptions: []
        }
      },
      props: {
        selectIds: {
          type: Set
        }
      },
      methods: {
        setMaintainerOptions() {
          this.$get('/user/project/member/list', response => {
            this.executorOptions = response.data;
          });
        },
        saveExecutor() {
          let param = {};
          param.executor = this.executor;
          if (this.executor === '') {
            this.$message.warning(this.$t('test_track.plan_view.select_executor'));
            return;
          }
          param.ids = [...this.selectIds];
          this.$post('/test/plan/case/batch/edit' , param, () => {
            this.executor = '';
            this.selectIds.clear();
            this.$success(this.$t('commons.save_success'));
            this.executorEditVisible = false;
            this.$emit('refresh');
          });
        },
        openExecutorEdit() {
          this.executorEditVisible = true;
          this.setMaintainerOptions();
        }
      }
    }
</script>

<style scoped>

</style>
