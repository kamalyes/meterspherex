<template>
  <el-col>
    <track-header-menus/>
    <div>
      <transition>
        <keep-alive>
          <router-view :baseUrl="baseUrl"/>
        </keep-alive>
      </transition>
    </div>
  </el-col>
</template>

<script>

import TrackHeaderMenus from "./head/TrackHeaderMenus";

export default {
  name: "TrackHome",
  components: {TrackHeaderMenus},
  data() {
    return {
      baseUrl: "track"
    }
  },
  activated() {
    this.$refs.table.doLayout()
  }
}
</script>


<style scoped>
.node-tree {
  margin: 3%;
}
</style>

