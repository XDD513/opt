<template>
  <div class="test-result" :class="[`mode-${mode}`, `variant-${variant}`, { embedded }]">
    <!-- 三栏嵌入式：不再渲染外层 el-card，避免重复 padding/margin 导致“挤成一条” -->
    <template v-if="embedded">
      <div class="embedded-content">
        <!-- 完成提示 -->
        <div v-if="showOverview" class="completion-section">
          <h2 class="completion-title">体质辨识报告</h2>
          <p class="completion-desc">基于舌象识别与问卷信息生成，结果仅用于健康管理参考</p>
        </div>
        
        <!-- 体质类型 -->
        <div v-if="showOverview" class="constitution-stats">
          <div class="main-stat-card">
            <div class="main-stat-top">
              <div class="main-stat-title">主要体质</div>
              <el-tag type="success" effect="light">已完成</el-tag>
            </div>
            <div class="main-stat-name">{{ testResult.primaryConstitutionName }}</div>
            <div class="main-stat-score">
              <span class="label">综合得分</span>
              <span class="value">{{ formatScore(testResult.primaryScore) }}</span>
              <span class="unit">分</span>
            </div>
          </div>

          <div class="side-stat-cards">
            <div class="side-stat-card">
              <div class="side-stat-title">次要体质</div>
              <div v-if="testResult.secondaryConstitutionName || testResult.secondaryConstitution" class="side-stat-value">
                <span class="name">{{ testResult.secondaryConstitutionName || testResult.secondaryConstitution }}</span>
                <span v-if="testResult.secondaryScore != null" class="score">({{ formatScore(testResult.secondaryScore) }}分)</span>
              </div>
              <div v-else class="side-stat-empty">无明显次要体质</div>
            </div>

            <div class="side-stat-card">
              <div class="side-stat-title">诊断流程状态</div>
              <div class="side-stat-tags">
                <el-tag size="small" type="primary" effect="plain">
                  {{ hasAiAnalysis ? '分析已生成' : '待生成分析' }}
                </el-tag>
                <el-tag size="small" type="success" effect="plain" style="margin-left: 8px;">
                  {{ hasAiPlans ? '计划已生成' : '待生成计划' }}
                </el-tag>
                <el-tag size="small" type="warning" effect="plain" style="margin-left: 8px;">
                  {{ hasAiRecipe ? '药膳已生成' : '待生成药膳' }}
                </el-tag>
              </div>
              <div class="side-stat-hint" v-if="!hasAiAnalysis">
                先生成深度分析，再生成健康计划。
              </div>
            </div>
          </div>
        </div>
        
        <!-- 分析和建议 -->
        <div v-if="isWorkspace && (showAnalysis || showRegimen)" class="analysis-section workspace-section">
          <!-- 生成后的深度分析 -->
          <div v-if="parsedAiSuggestion" class="workspace-cards">
            <div v-if="showAnalysis" class="ws-card ws-card-wide">
              <div class="ws-card-title">深度分析</div>
              <div class="ws-card-body">
                <p class="analysis-paragraph" v-if="animatedAnalysisText">{{ animatedAnalysisText }}</p>
                <p class="analysis-paragraph" v-else-if="parsedAiSuggestion.analysis">{{ parsedAiSuggestion.analysis }}</p>
                <p class="analysis-paragraph" v-if="!parsedAiSuggestion.analysis && !parsedAiSuggestion.summary">
                  （待生成）
                </p>
              </div>
            </div>

            <!-- 最近一次已入库的药膳 -->
            <div v-if="showRegimen && props.latestRecipe" class="ws-card">
              <div class="ws-card-title">药膳建议</div>
              <div class="ws-card-body">
                <div class="recipe-brief">
                  <div class="recipe-name">【{{ props.latestRecipe.recipeName }}】</div>
                  <div class="recipe-efficacy" v-if="props.latestRecipe.efficacy">
                    <strong>功效：</strong>{{ props.latestRecipe.efficacy }}
                  </div>
                  <div class="recipe-actions" style="margin-top: 8px;">
                    <router-link :to="`/patient/recipe/detail/${props.latestRecipe.id}`">
                      <el-button type="primary" size="small">查看详情</el-button>
                    </router-link>
                    <router-link to="/patient/recipe" style="margin-left: 8px;">
                      <el-button size="small">更多药膳</el-button>
                    </router-link>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="showDietSection" class="ws-card">
              <div class="ws-card-title">饮食宜忌</div>
              <div class="ws-card-body">
                <div class="diet-content">
                  <div v-if="dietRecommendToRender.length">
                    <span class="label suitable">宜：</span>
                    <span
                      v-for="(item, i) in dietRecommendToRender"
                      :key="'rec_ws_'+i"
                      class="diet-item"
                    >{{ item }}<span v-if="i < dietRecommendToRender.length-1">、</span></span>
                  </div>
                  <div v-if="dietAvoidToRender.length" style="margin-top: 8px;">
                    <span class="label avoid">忌：</span>
                    <span
                      v-for="(item, i) in dietAvoidToRender"
                      :key="'avoid_ws_'+i"
                      class="diet-item"
                    >{{ item }}<span v-if="i < dietAvoidToRender.length-1">、</span></span>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="showLifestyleSection" class="ws-card">
              <div class="ws-card-title">起居调养</div>
              <div class="ws-card-body">
                <div v-for="(item, i) in lifestyleToRender" :key="'life_ws_'+i" class="ws-reveal-item regimen-line">{{ item }}</div>
              </div>
            </div>

            <div v-if="showAcupointsSection" class="ws-card ws-card-wide">
              <div class="ws-card-title">穴位保健</div>
              <div class="ws-card-body">
                <div class="ws-acupoint-grid">
                  <div v-for="(pt, i) in acupointsToRender" :key="'ap_ws_'+i" class="ws-acupoint-card ws-reveal-card">
                    <div class="ws-acupoint-header">
                      <div class="ws-acupoint-badge">穴位</div>
                      <div class="ws-acupoint-name">{{ pt.name }}</div>
                    </div>
                    <div class="ws-acupoint-meta">
                      <div v-if="pt.location" class="ws-acupoint-row">
                        <span class="k">定位</span>
                        <span class="v">{{ pt.location }}</span>
                      </div>
                      <div class="ws-acupoint-row">
                        <span class="k">方法/功效</span>
                        <span class="v">{{ pt.effect }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 批量未入库药膳：外层大容器（标题+一键保存）+ 内层每道药膳卡片 -->
            <div
              v-if="showRegimen && recipesToRender.length"
              class="ws-card ws-card-wide ws-batch-recipes"
            >
              <div class="ws-batch-recipes-header">
                <div class="ws-card-title">药膳建议</div>
                <div class="ws-batch-recipes-actions">
                  <el-button
                    type="success"
                    size="small"
                    :loading="savingBatchRecipes"
                    :disabled="savingBatchRecipes"
                    @click="handleSaveBatchRecipes"
                  >
                    一键保存药膳
                  </el-button>
                </div>
              </div>

              <div class="ws-card-body">
                <div class="ws-batch-recipes-grid">
                  <div
                    v-for="(rec, idx) in recipesToRender"
                    :key="'unp_'+idx"
                    class="ws-card ws-batch-recipe-card ws-reveal-card"
                  >
                    <div class="ws-card-body">
                      <div class="ws-recipe-name">【{{ rec.name || ('药膳 ' + (idx + 1)) }}】</div>
                      <div v-if="rec.ingredients && rec.ingredients.length">
                        <strong>食材：</strong>
                        <span v-for="(ing,i) in rec.ingredients" :key="'ing_'+i">
                          {{ ing.name }}（{{ ing.amount }}）<span v-if="i<rec.ingredients.length-1">、</span>
                        </span>
                      </div>
                      <div v-if="rec.steps && rec.steps.length" style="margin-top: 4px;">
                        <strong>做法：</strong>
                        <ol style="margin: 4px 0 0 18px;">
                          <li v-for="(s,i) in rec.steps" :key="'st_'+i">{{ s }}</li>
                        </ol>
                      </div>
                      <div v-if="rec.efficacy" style="margin-top: 4px;"><strong>功效：</strong>{{ rec.efficacy }}</div>
                      <div v-if="rec.contraindications && rec.contraindications.length" style="margin-top: 4px;">
                        <strong>禁忌：</strong>
                        <span v-for="(c,i) in rec.contraindications" :key="'ct_'+i">{{ c }}<span v-if="i<rec.contraindications.length-1">、</span></span>
                      </div>
                      <div v-if="rec.raw && !rec.name" style="white-space: pre-wrap; margin-top: 6px; color:#475569;">
                        {{ rec.raw }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-else-if="showRegimen && !props.latestRecipe"
              class="ws-card ws-card-wide ws-card-placeholder"
            >
              <div class="ws-card-title">药膳列表</div>
              <div class="ws-card-body ws-card-placeholder-body">（待生成）</div>
            </div>
          </div>

          <!-- 解析兜底：若已生成但解析失败，展示原始内容，避免“已生成却看不到结果” -->
          <div v-else-if="hasAiAnalysis || hasAiPlans || hasAiRecipe" class="workspace-cards">
            <div class="ws-card ws-card-wide">
              <div class="ws-card-title">结构化结果（原始内容）</div>
              <div class="ws-card-body">
                <div class="analysis-paragraph" style="white-space: pre-wrap;">
                  {{ testResult.healthSuggestion || '暂无可展示内容' }}
                </div>
              </div>
            </div>
          </div>

          <!-- 空占位：仅在完全未生成时展示 -->
          <div v-else class="workspace-cards">
            <div v-if="showAnalysis" class="ws-card ws-card-placeholder">
              <div class="ws-card-title">体质深度分析</div>
              <div class="ws-card-body ws-card-placeholder-body">（待生成）</div>
            </div>

            <div v-if="showRegimen" class="ws-card ws-card-wide ws-card-placeholder">
              <div class="ws-card-title">药膳建议</div>
              <div class="ws-card-body ws-card-placeholder-body">（待生成）</div>
            </div>
          </div>
        </div>

        <div v-else-if="showAnalysis || showRegimen" class="analysis-section">
          <!-- 两列都显示时才使用两栏网格；单列时全宽显示，避免“挤成一条” -->
          <div v-if="showAnalysis && showRegimen" class="analysis-grid">
            <!-- 左列：深度分析/总体原则 -->
            <div class="analysis-col">
              <div class="analysis-item">
            <div class="item-title">
              <div class="title-left">
                <el-icon><MagicStick /></el-icon>
                <span>AI 实时深度分析</span>
                <el-tag size="small" type="success" effect="plain" v-if="streamingContent || isAiLoading">
                  <template v-if="isAiLoading && streamPhase === 'plans'">正在生成健康计划…</template>
                  <template v-else-if="isAiLoading && !streamingContent">连接中…</template>
                  <template v-else-if="isAiLoading">生成深度分析中…</template>
                  <template v-else>输出中</template>
                </el-tag>
                <el-tag size="small" type="success" effect="plain" v-else-if="hasAiAnalysis">已生成</el-tag>
              </div>
              <div v-if="showSectionActions" class="title-actions">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  :loading="isAiLoading && streamPhase === 'analysis'"
                  :disabled="isAiLoading || hasAiAnalysis"
                  @click="$emit('generate-analysis')"
                >
                  {{ hasAiAnalysis ? '深度分析已生成' : '生成深度分析' }}
                </el-button>
              </div>
            </div>
            
            <!-- 生成中的内容展示 -->
            <div v-if="showStreamingPreview" class="ai-streaming-content" v-loading="isAiLoading && !streamingContent && streamPhase !== 'plans'" element-loading-text="AI 专家正在接入..." element-loading-background="rgba(255, 255, 255, 0.8)">
              <div class="streaming-text">{{ formattedStreamingContent }}<span class="cursor" v-if="streamingContent">|</span></div>
              <div v-if="isAiLoading && !streamingContent && streamPhase !== 'plans'" style="height: 60px;"></div>
            </div>

            <!-- 生成完成后的结构化展示 -->
            <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
              <div class="summary-box" v-if="parsedAiSuggestion.analysis">
                <div class="sub-title">【体质深度分析】</div>
                <p class="analysis-paragraph">{{ animatedAnalysisText || parsedAiSuggestion.analysis }}</p>
              </div>

              <div class="summary-box" v-if="parsedAiSuggestion.summary">
                <div class="sub-title">【总体原则】</div>
                <p class="analysis-paragraph">{{ animatedSummaryText || parsedAiSuggestion.summary }}</p>
              </div>
            </div>

            <!-- 非结构化 AI 建议展示 (当无法解析为 JSON 时显示) -->
            <div v-else-if="testResult.healthSuggestion" class="ai-streaming-content" style="border-left-color: #67c23a;">
               <div class="streaming-text" style="white-space: pre-wrap;">{{ testResult.healthSuggestion }}</div>
            </div>
            
            <!-- 兜底：显示普通列表 -->
            <ul v-else class="suggestions-list">
              <li v-for="(suggestion, idx) in testResult.suggestions" :key="idx">
                {{ suggestion }}
              </li>
            </ul>
              </div>
            </div>

            <!-- 右列：饮食/起居/穴位 -->
            <div class="analysis-col">
              <div class="analysis-item">
                <div class="item-title compact">
                  <div class="title-left">
                    <span>调养要点</span>
                  </div>
                </div>

                <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
                  <div class="diet-box" v-if="parsedAiSuggestion.diet">
                    <div class="sub-title">【饮食宜忌】</div>
                    <div class="diet-content">
                      <div v-if="parsedAiSuggestion.diet.recommend && parsedAiSuggestion.diet.recommend.length">
                        <span class="label suitable">宜：</span>
                        <span v-for="(item, i) in parsedAiSuggestion.diet.recommend" :key="'rec'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.recommend.length-1">、</span></span>
                      </div>
                      <div v-if="parsedAiSuggestion.diet.avoid && parsedAiSuggestion.diet.avoid.length" style="margin-top: 8px;">
                        <span class="label avoid">忌：</span>
                        <span v-for="(item, i) in parsedAiSuggestion.diet.avoid" :key="'avoid'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.avoid.length-1">、</span></span>
                      </div>
                    </div>
                  </div>

                  <div class="lifestyle-box" v-if="parsedAiSuggestion.lifestyle && parsedAiSuggestion.lifestyle.length">
                    <div class="sub-title">【起居调养】</div>
                    <ul class="simple-list">
                      <li v-for="(item, i) in parsedAiSuggestion.lifestyle" :key="'life'+i">{{ item }}</li>
                    </ul>
                  </div>
                  
                  <div class="acupoints-box" v-if="displayAcupoints.length">
                    <div class="sub-title">【穴位保健】</div>
                    <div class="structured-acupoints">
                      <div v-for="(pt, i) in displayAcupoints" :key="'ap'+i" class="point-item">
                        <div class="point-name">【{{ pt.name }}】</div>
                        <div class="point-detail">
                          <p v-if="pt.location"><strong>定位：</strong>{{ pt.location }}</p>
                          <p><strong>方法/功效：</strong>{{ pt.effect }}</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-else class="plans-empty">
                  <el-alert type="info" :closable="false" show-icon title="生成深度分析后显示调养要点">
                    这里会展示饮食宜忌、起居调养与穴位保健等内容。
                  </el-alert>
                </div>
              </div>
            </div>
          </div>

          <!-- 仅深度分析：单列全宽 -->
          <div v-else-if="showAnalysis" class="analysis-item">
            <div class="item-title">
              <div class="title-left">
                <el-icon><MagicStick /></el-icon>
                <span>AI 实时深度分析</span>
                <el-tag size="small" type="success" effect="plain" v-if="streamingContent || isAiLoading">
                  <template v-if="isAiLoading && streamPhase === 'plans'">正在生成健康计划…</template>
                  <template v-else-if="isAiLoading && !streamingContent">连接中…</template>
                  <template v-else-if="isAiLoading">生成深度分析中…</template>
                  <template v-else>输出中</template>
                </el-tag>
                <el-tag size="small" type="success" effect="plain" v-else-if="hasAiAnalysis">已生成</el-tag>
              </div>
              <div v-if="showSectionActions" class="title-actions">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  :loading="isAiLoading && streamPhase === 'analysis'"
                  :disabled="isAiLoading || hasAiAnalysis"
                  @click="$emit('generate-analysis')"
                >
                  {{ hasAiAnalysis ? '深度分析已生成' : '生成深度分析' }}
                </el-button>
              </div>
            </div>

            <div v-if="showStreamingPreview" class="ai-streaming-content ai-report-box" v-loading="isAiLoading && !streamingContent && streamPhase !== 'plans'" element-loading-text="AI 专家正在接入..." element-loading-background="rgba(255, 255, 255, 0.8)">
              <div class="streaming-text">{{ formattedStreamingContent }}<span class="cursor" v-if="streamingContent">|</span></div>
              <div v-if="isAiLoading && !streamingContent && streamPhase !== 'plans'" style="height: 60px;"></div>
            </div>

            <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
              <div class="summary-box" v-if="parsedAiSuggestion.analysis">
                <div class="sub-title">【体质深度分析】</div>
                <p class="analysis-paragraph">{{ parsedAiSuggestion.analysis }}</p>
              </div>

              <div class="summary-box" v-if="parsedAiSuggestion.summary">
                <div class="sub-title">【总体原则】</div>
                <p class="analysis-paragraph">{{ parsedAiSuggestion.summary }}</p>
              </div>
            </div>

            <div v-else-if="testResult.healthSuggestion" class="ai-streaming-content ai-report-box" style="border-left-color: #67c23a;">
              <div class="streaming-text" style="white-space: pre-wrap;">{{ testResult.healthSuggestion }}</div>
            </div>

            <ul v-else class="suggestions-list">
              <li v-for="(suggestion, idx) in testResult.suggestions" :key="idx">
                {{ suggestion }}
              </li>
            </ul>
          </div>

          <!-- 仅调养要点：单列全宽 -->
          <div v-else class="analysis-item">
            <div class="item-title compact">
              <div class="title-left">
                <span>调养要点</span>
              </div>
            </div>

            <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
              <div class="diet-box" v-if="parsedAiSuggestion.diet">
                <div class="sub-title">【饮食宜忌】</div>
                <div class="diet-content">
                  <div v-if="parsedAiSuggestion.diet.recommend && parsedAiSuggestion.diet.recommend.length">
                    <span class="label suitable">宜：</span>
                    <span v-for="(item, i) in parsedAiSuggestion.diet.recommend" :key="'rec'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.recommend.length-1">、</span></span>
                  </div>
                  <div v-if="parsedAiSuggestion.diet.avoid && parsedAiSuggestion.diet.avoid.length" style="margin-top: 8px;">
                    <span class="label avoid">忌：</span>
                    <span v-for="(item, i) in parsedAiSuggestion.diet.avoid" :key="'avoid'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.avoid.length-1">、</span></span>
                  </div>
                </div>
              </div>

              <div class="lifestyle-box" v-if="parsedAiSuggestion.lifestyle && parsedAiSuggestion.lifestyle.length">
                <div class="sub-title">【起居调养】</div>
                <ul class="simple-list">
                  <li v-for="(item, i) in parsedAiSuggestion.lifestyle" :key="'life'+i">{{ item }}</li>
                </ul>
              </div>
              
              <div class="acupoints-box" v-if="displayAcupoints.length">
                <div class="sub-title">【穴位保健】</div>
                <div class="structured-acupoints">
                  <div v-for="(pt, i) in displayAcupoints" :key="'ap'+i" class="point-item">
                    <div class="point-name">【{{ pt.name }}】</div>
                    <div class="point-detail">
                      <p v-if="pt.location"><strong>定位：</strong>{{ pt.location }}</p>
                      <p><strong>方法/功效：</strong>{{ pt.effect }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="plans-empty">
              <el-alert type="info" :closable="false" show-icon title="生成深度分析后显示调养要点">
                这里会展示饮食宜忌、起居调养与穴位保健等内容。
              </el-alert>
            </div>
          </div>
        </div>

        <!-- 健康计划：整行展示（更适合卡片列表） -->
        <div class="plans-section" v-if="showPlans">
          <div class="plans-section-header">
            <div class="plans-section-title">推荐健康计划</div>
            <div class="plans-title-actions">
              <el-button
                v-if="showSectionActions"
                type="success"
                size="small"
                plain
                :loading="isAiLoading && streamPhase === 'plans'"
                :disabled="!hasAiAnalysis || isAiLoading || hasAiPlans"
                @click="$emit('generate-plans')"
              >
                {{ hasAiPlans ? '健康计划已生成' : '生成健康计划' }}
              </el-button>
              <el-button
                v-if="hasAiPlans && !plansSaved"
                type="success"
                size="small"
                @click="handleSavePlans"
                :loading="savingPlans"
              >
                一键保存计划
              </el-button>
              <el-tag v-if="hasAiPlans && plansSaved" type="success">计划已保存</el-tag>
            </div>
          </div>

          <div v-if="hasAiPlans" class="plans-list">
            <div v-for="(plan, i) in plansToRender" :key="'plan'+i" class="plan-card ws-reveal-card">
              <div class="plan-card-header">
                <span class="plan-card-title">{{ plan.planName || plan.name }}</span>
                <el-tag size="small" :type="getPlanTypeTag(plan.planType || plan.type)" effect="light">
                  {{ getPlanTypeLabel(plan.planType || plan.type) }}计划
                </el-tag>
              </div>
              
              <div class="plan-card-desc">{{ plan.description }}</div>
              
              <div class="plan-card-info">
                <div class="info-row target">
                  <el-icon><Aim /></el-icon>
                  <span class="label">目标:</span>
                  <span class="value">{{ plan.targetContent || '无特定目标' }}</span>
                </div>
                <div class="info-row date">
                  <el-icon><Calendar /></el-icon>
                  <span class="label">周期:</span>
                  <span class="value">{{ getPlanDateRange(plan.duration) }}</span>
                </div>
                <div class="info-row freq">
                  <el-icon><Timer /></el-icon>
                  <span class="label">频率:</span>
                  <span class="value">{{ formatFrequency(plan.frequency) }}</span>
                </div>
              </div>

              <div class="plan-card-progress">
                <el-progress :percentage="0" :stroke-width="6" :show-text="false" color="#409eff" />
                <div class="progress-labels"><span>0%</span></div>
              </div>

            </div>
          </div>

          <!-- 空占位：展示计划列表的空区域 -->
          <div v-else class="plans-empty">
            <div class="ws-card-placeholder-body" style="padding: 8px 0;">
              （待生成）
            </div>
          </div>
        </div>
        
        <!-- 操作按钮 -->
        <div v-if="showFooterActions" class="actions-section">
          <el-button type="primary" @click="handleViewHistory">查看测试历史</el-button>
        </div>
      </div>
    </template>

    <!-- 完整页面：保留卡片容器 -->
    <el-card v-else class="result-card" style="border-left: 4px solid #67c23a;">
      <template v-if="showHeader" #header>
        <div class="card-header">
          <span>{{ headerTitle }}</span>
          <el-tag type="success">已完成</el-tag>
        </div>
      </template>
      
      <div class="result-content">
        <!-- 完成提示 -->
        <div v-if="showOverview" class="completion-section">
          <h2 class="completion-title">体质辨识报告</h2>
          <p class="completion-desc">基于舌象识别与问卷信息生成，结果仅用于健康管理参考</p>
        </div>
        
        <!-- 体质类型 -->
        <div v-if="showOverview" class="constitution-stats">
          <div class="main-stat-card">
            <div class="main-stat-top">
              <div class="main-stat-title">主要体质</div>
              <el-tag type="success" effect="light">已完成</el-tag>
            </div>
            <div class="main-stat-name">{{ testResult.primaryConstitutionName }}</div>
            <div class="main-stat-score">
              <span class="label">综合得分</span>
              <span class="value">{{ formatScore(testResult.primaryScore) }}</span>
              <span class="unit">分</span>
            </div>
          </div>

          <div class="side-stat-cards">
            <div class="side-stat-card">
              <div class="side-stat-title">次要体质</div>
              <div v-if="testResult.secondaryConstitutionName || testResult.secondaryConstitution" class="side-stat-value">
                <span class="name">{{ testResult.secondaryConstitutionName || testResult.secondaryConstitution }}</span>
                <span v-if="testResult.secondaryScore != null" class="score">({{ formatScore(testResult.secondaryScore) }}分)</span>
              </div>
              <div v-else class="side-stat-empty">无明显次要体质</div>
            </div>

            <div class="side-stat-card">
              <div class="side-stat-title">AI 状态</div>
              <div class="side-stat-tags">
                <el-tag size="small" type="primary" effect="plain">
                  {{ hasAiAnalysis ? '分析已生成' : '待生成分析' }}
                </el-tag>
                <el-tag size="small" type="success" effect="plain" style="margin-left: 8px;">
                  {{ hasAiPlans ? '计划已生成' : '待生成计划' }}
                </el-tag>
              </div>
              <div class="side-stat-hint" v-if="!hasAiAnalysis">
                先生成深度分析，再生成健康计划。
              </div>
            </div>
          </div>
        </div>
        
        <!-- 分析和建议 -->
        <div v-if="showAnalysis || showRegimen" class="analysis-section">
          <!-- 两列都显示时才使用两栏网格；单列时全宽显示，避免“挤成一条” -->
          <div v-if="showAnalysis && showRegimen" class="analysis-grid">
            <!-- 左列：深度分析/总体原则 -->
            <div class="analysis-col">
              <div class="analysis-item">
            <div class="item-title">
              <div class="title-left">
                <el-icon><MagicStick /></el-icon>
                <span>AI 实时深度分析</span>
                <el-tag size="small" type="success" effect="plain" v-if="streamingContent || isAiLoading">
                  <template v-if="isAiLoading && streamPhase === 'plans'">正在生成健康计划…</template>
                  <template v-else-if="isAiLoading && !streamingContent">连接中…</template>
                  <template v-else-if="isAiLoading">生成深度分析中…</template>
                  <template v-else>输出中</template>
                </el-tag>
                <el-tag size="small" type="success" effect="plain" v-else-if="hasAiAnalysis">已生成</el-tag>
              </div>
              <div v-if="showSectionActions" class="title-actions">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  :loading="isAiLoading && streamPhase === 'analysis'"
                  :disabled="isAiLoading || hasAiAnalysis"
                  @click="$emit('generate-analysis')"
                >
                  {{ hasAiAnalysis ? '深度分析已生成' : '生成深度分析' }}
                </el-button>
              </div>
            </div>
            
            <!-- 生成中的内容展示 -->
            <div v-if="showStreamingPreview" class="ai-streaming-content ai-report-box" v-loading="isAiLoading && !streamingContent && streamPhase !== 'plans'" element-loading-text="AI 专家正在接入..." element-loading-background="rgba(255, 255, 255, 0.8)">
              <div class="streaming-text">{{ formattedStreamingContent }}<span class="cursor" v-if="streamingContent">|</span></div>
              <div v-if="isAiLoading && !streamingContent && streamPhase !== 'plans'" style="height: 60px;"></div>
            </div>

            <!-- 生成完成后的结构化展示 -->
            <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
              <div class="summary-box" v-if="parsedAiSuggestion.analysis">
                <div class="sub-title">【体质深度分析】</div>
                <p class="analysis-paragraph">{{ parsedAiSuggestion.analysis }}</p>
              </div>

              <div class="summary-box" v-if="parsedAiSuggestion.summary">
                <div class="sub-title">【总体原则】</div>
                <p class="analysis-paragraph">{{ parsedAiSuggestion.summary }}</p>
              </div>
            </div>

            <!-- 非结构化 AI 建议展示 (当无法解析为 JSON 时显示) -->
            <div v-else-if="testResult.healthSuggestion" class="ai-streaming-content ai-report-box" style="border-left-color: #67c23a;">
               <div class="streaming-text" style="white-space: pre-wrap;">{{ testResult.healthSuggestion }}</div>
            </div>
            
            <!-- 兜底：显示普通列表 -->
            <ul v-else class="suggestions-list">
              <li v-for="(suggestion, idx) in testResult.suggestions" :key="idx">
                {{ suggestion }}
              </li>
            </ul>
              </div>
            </div>

            <!-- 右列：饮食/起居/穴位 -->
            <div class="analysis-col">
              <div class="analysis-item">
                <div class="item-title compact">
                  <div class="title-left">
                    <span>调养要点</span>
                  </div>
                </div>

                <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
                  <div class="diet-box" v-if="parsedAiSuggestion.diet">
                    <div class="sub-title">【饮食宜忌】</div>
                    <div class="diet-content">
                      <div v-if="parsedAiSuggestion.diet.recommend && parsedAiSuggestion.diet.recommend.length">
                        <span class="label suitable">宜：</span>
                        <span v-for="(item, i) in parsedAiSuggestion.diet.recommend" :key="'rec'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.recommend.length-1">、</span></span>
                      </div>
                      <div v-if="parsedAiSuggestion.diet.avoid && parsedAiSuggestion.diet.avoid.length" style="margin-top: 8px;">
                        <span class="label avoid">忌：</span>
                        <span v-for="(item, i) in parsedAiSuggestion.diet.avoid" :key="'avoid'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.avoid.length-1">、</span></span>
                      </div>
                    </div>
                  </div>

                  <div class="lifestyle-box" v-if="parsedAiSuggestion.lifestyle && parsedAiSuggestion.lifestyle.length">
                    <div class="sub-title">【起居调养】</div>
                    <ul class="simple-list">
                      <li v-for="(item, i) in parsedAiSuggestion.lifestyle" :key="'life'+i">{{ item }}</li>
                    </ul>
                  </div>
                  
                  <div class="acupoints-box" v-if="displayAcupoints.length">
                    <div class="sub-title">【穴位保健】</div>
                    <div class="structured-acupoints">
                      <div v-for="(pt, i) in displayAcupoints" :key="'ap'+i" class="point-item">
                        <div class="point-name">【{{ pt.name }}】</div>
                        <div class="point-detail">
                          <p v-if="pt.location"><strong>定位：</strong>{{ pt.location }}</p>
                          <p><strong>方法/功效：</strong>{{ pt.effect }}</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-else class="plans-empty">
                  <el-alert type="info" :closable="false" show-icon title="生成深度分析后显示调养要点">
                    这里会展示饮食宜忌、起居调养与穴位保健等内容。
                  </el-alert>
                </div>
              </div>
            </div>
          </div>

          <!-- 仅深度分析：单列全宽 -->
          <div v-else-if="showAnalysis" class="analysis-item">
            <div class="item-title">
              <div class="title-left">
                <el-icon><MagicStick /></el-icon>
                <span>AI 实时深度分析</span>
                <el-tag size="small" type="success" effect="plain" v-if="streamingContent || isAiLoading">
                  <template v-if="isAiLoading && streamPhase === 'plans'">正在生成健康计划…</template>
                  <template v-else-if="isAiLoading && !streamingContent">连接中…</template>
                  <template v-else-if="isAiLoading">生成深度分析中…</template>
                  <template v-else>输出中</template>
                </el-tag>
                <el-tag size="small" type="success" effect="plain" v-else-if="hasAiAnalysis">已生成</el-tag>
              </div>
              <div v-if="showSectionActions" class="title-actions">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  :loading="isAiLoading && streamPhase === 'analysis'"
                  :disabled="isAiLoading || hasAiAnalysis"
                  @click="$emit('generate-analysis')"
                >
                  {{ hasAiAnalysis ? '深度分析已生成' : '生成深度分析' }}
                </el-button>
          </div>
        </div>

            <div v-if="showStreamingPreview" class="ai-streaming-content ai-report-box" v-loading="isAiLoading && !streamingContent && streamPhase !== 'plans'" element-loading-text="AI 专家正在接入..." element-loading-background="rgba(255, 255, 255, 0.8)">
              <div class="streaming-text">{{ formattedStreamingContent }}<span class="cursor" v-if="streamingContent">|</span></div>
              <div v-if="isAiLoading && !streamingContent && streamPhase !== 'plans'" style="height: 60px;"></div>
            </div>

            <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
              <div class="summary-box" v-if="parsedAiSuggestion.analysis">
                <div class="sub-title">【体质深度分析】</div>
                <p class="analysis-paragraph">{{ parsedAiSuggestion.analysis }}</p>
              </div>

              <div class="summary-box" v-if="parsedAiSuggestion.summary">
                <div class="sub-title">【总体原则】</div>
                <p class="analysis-paragraph">{{ parsedAiSuggestion.summary }}</p>
              </div>
            </div>

            <div v-else-if="testResult.healthSuggestion" class="ai-streaming-content ai-report-box" style="border-left-color: #67c23a;">
              <div class="streaming-text" style="white-space: pre-wrap;">{{ testResult.healthSuggestion }}</div>
            </div>

            <ul v-else class="suggestions-list">
              <li v-for="(suggestion, idx) in testResult.suggestions" :key="idx">
                {{ suggestion }}
              </li>
            </ul>
          </div>

          <!-- 仅调养要点：单列全宽 -->
          <div v-else class="analysis-item">
            <div class="item-title compact">
              <div class="title-left">
                <span>调养要点</span>
              </div>
            </div>

            <div v-if="parsedAiSuggestion" class="structured-ai-suggestion">
              <div class="diet-box" v-if="parsedAiSuggestion.diet">
                <div class="sub-title">【饮食宜忌】</div>
                <div class="diet-content">
                  <div v-if="parsedAiSuggestion.diet.recommend && parsedAiSuggestion.diet.recommend.length">
                    <span class="label suitable">宜：</span>
                    <span v-for="(item, i) in parsedAiSuggestion.diet.recommend" :key="'rec'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.recommend.length-1">、</span></span>
                  </div>
                  <div v-if="parsedAiSuggestion.diet.avoid && parsedAiSuggestion.diet.avoid.length" style="margin-top: 8px;">
                    <span class="label avoid">忌：</span>
                    <span v-for="(item, i) in parsedAiSuggestion.diet.avoid" :key="'avoid'+i" class="diet-item">{{ item }}<span v-if="i < parsedAiSuggestion.diet.avoid.length-1">、</span></span>
                  </div>
                </div>
              </div>

              <div class="lifestyle-box" v-if="parsedAiSuggestion.lifestyle && parsedAiSuggestion.lifestyle.length">
                <div class="sub-title">【起居调养】</div>
                <ul class="simple-list">
                  <li v-for="(item, i) in parsedAiSuggestion.lifestyle" :key="'life'+i">{{ item }}</li>
                </ul>
              </div>
              
              <div class="acupoints-box" v-if="displayAcupoints.length">
                <div class="sub-title">【穴位保健】</div>
                <div class="structured-acupoints">
                  <div v-for="(pt, i) in displayAcupoints" :key="'ap'+i" class="point-item">
                    <div class="point-name">【{{ pt.name }}】</div>
                    <div class="point-detail">
                      <p v-if="pt.location"><strong>定位：</strong>{{ pt.location }}</p>
                      <p><strong>方法/功效：</strong>{{ pt.effect }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="plans-empty">
              <el-alert type="info" :closable="false" show-icon title="生成深度分析后显示调养要点">
                这里会展示饮食宜忌、起居调养与穴位保健等内容。
              </el-alert>
            </div>
          </div>
        </div>

        <div class="plans-section" v-if="showPlans && (parsedAiSuggestion || hasAiAnalysis || isAiLoading)">
          <div class="plans-section-header">
            <div class="plans-section-title">推荐健康计划</div>
            <div class="plans-title-actions">
              <el-button
                type="success"
                size="small"
                plain
                :loading="isAiLoading && streamPhase === 'plans'"
                :disabled="!hasAiAnalysis || isAiLoading || hasAiPlans"
                @click="$emit('generate-plans')"
              >
                {{ hasAiPlans ? '健康计划已生成' : '生成健康计划' }}
              </el-button>
              <el-button
                v-if="hasAiPlans && !plansSaved"
                type="success"
                size="small"
                @click="handleSavePlans"
                :loading="savingPlans"
              >
                一键保存计划
              </el-button>
              <el-tag v-if="hasAiPlans && plansSaved" type="success">计划已保存</el-tag>
            </div>
          </div>

          <div v-if="hasAiPlans" class="plans-list">
            <div v-for="(plan, i) in plansToRender" :key="'plan'+i" class="plan-card ws-reveal-card">
              <div class="plan-card-header">
                <span class="plan-card-title">{{ plan.planName || plan.name }}</span>
                <el-tag size="small" :type="getPlanTypeTag(plan.planType || plan.type)" effect="light">
                  {{ getPlanTypeLabel(plan.planType || plan.type) }}计划
                </el-tag>
              </div>
              
              <div class="plan-card-desc">{{ plan.description }}</div>
              
              <div class="plan-card-info">
                <div class="info-row target">
                  <el-icon><Aim /></el-icon>
                  <span class="label">目标:</span>
                  <span class="value">{{ plan.targetContent || '无特定目标' }}</span>
                </div>
                <div class="info-row date">
                  <el-icon><Calendar /></el-icon>
                  <span class="label">周期:</span>
                  <span class="value">{{ getPlanDateRange(plan.duration) }}</span>
                </div>
                <div class="info-row freq">
                  <el-icon><Timer /></el-icon>
                  <span class="label">频率:</span>
                  <span class="value">{{ formatFrequency(plan.frequency) }}</span>
                </div>
              </div>

              <div class="plan-card-progress">
                <el-progress :percentage="0" :stroke-width="6" :show-text="false" color="#409eff" />
                <div class="progress-labels"><span>0%</span></div>
              </div>

            </div>
          </div>

          <div v-else class="plans-empty">
            <el-alert
              v-if="!hasAiAnalysis"
              type="info"
              :closable="false"
              show-icon
              title="先生成深度分析"
            >
              生成健康计划前需要先生成深度分析报告。
            </el-alert>
            <el-alert
              v-else
              type="warning"
              :closable="false"
              show-icon
              title="健康计划尚未生成"
            >
              点击右上角「生成健康计划」获取可执行的饮食/运动/穴位/起居计划。
            </el-alert>
          </div>
        </div>
        
        <div v-if="showFooterActions" class="actions-section">
          <el-button type="primary" @click="handleViewHistory">查看测试历史</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { createHealthPlan } from '@/api/health'
import { favoriteRecipe, getRecipesByTestId, saveRecipeFromJson, saveRecipeFromSuggestion } from '@/api/recipe'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { MagicStick, Aim, Calendar, Timer } from '@element-plus/icons-vue'
import { parseAiHealthSuggestion, normalizeAcupointsList } from '@/utils/parseAiHealthJson'

/**
 * @typedef {Object} TestResult
 * @property {string} primaryConstitutionName - 主要体质名称
 * @property {number} primaryScore - 主要体质得分
 * @property {string|null} secondaryConstitutionName - 次要体质名称
 * @property {number|null} secondaryScore - 次要体质得分
 * @property {string} analysis - 体质分析
 * @property {string[]} suggestions - 调养建议列表
 */

/** AI 生成状态相关 */
const props = defineProps({
  /** @type {import('vue').PropType<TestResult>} */
  testResult: {
    type: Object,
    required: true
  },
  /** AI 生成中的文本（当前版本未使用） */
  streamingContent: {
    type: String,
    default: ''
  },
  /** AI 加载状态 */
  isAiLoading: {
    type: Boolean,
    default: false
  },
  /** analysis | plans | null */
  streamPhase: {
    type: String,
    default: null
  },
  /**
   * full: 全量报告
   * analysis: 仅深度分析
   * plans: 仅健康计划 + 穴位（调养要点）
   */
  mode: {
    type: String,
    default: 'full'
  },
  /**
   * 是否显示卡片 header（用于三栏布局时避免重复标题）
   */
  showHeader: {
    type: Boolean,
    default: true
  },
  /**
   * 三栏布局内嵌模式：不渲染外层 el-card/padding/margin
   */
  embedded: {
    type: Boolean,
    default: false
  },
  /** 最近一次“按提示词生成”的药膳原文（未入库） */
  latestRecipeText: {
    type: String,
    default: ''
  },
  /** 批量未入库药膳的统一结构列表（仅展示） */
  unpersistedRecipes: {
    type: Array,
    default: () => []
  },
  /** 批量药膳是否已保存（用于保留展示并切换按钮状态） */
  batchRecipesSaved: {
    type: Boolean,
    default: false
  },
  /** 外部状态：药膳列表是否已生成（可选） */
  recipeReady: {
    type: Boolean,
    default: undefined
  },
  /**
   * 视觉变体：
   * default: 常规报告
   * workspace: 工作台（右侧主舞台专用）
   */
  variant: {
    type: String,
    default: 'default'
  },
  /**
   * 生成按钮位置：
   * section: 各自模块内（默认）
   * top: 由外部主舞台控制条承载（组件内部隐藏生成按钮）
   */
  actionsPlacement: {
    type: String,
    default: 'section'
  },
  /** 外部状态：深度分析是否已生成（可选） */
  analysisReady: {
    type: Boolean,
    default: undefined
  },
  /** 外部状态：健康计划是否已生成（可选） */
  plansReady: {
    type: Boolean,
    default: undefined
  },
  /** 最近一次AI生成并保存的药膳（可选） */
  latestRecipe: {
    type: Object,
    default: null
  },
  /**
   * 递增时：从当前已入库的 healthSuggestion 重播工作台入场动画（离页后台生成完成后回到页面）
   */
  revealReplayNonce: {
    type: Number,
    default: 0
  }
})

const isWorkspace = computed(() => props.variant === 'workspace')

const headerTitle = computed(() => {
  if (props.mode === 'analysis') return '深度分析'
  if (props.mode === 'plans') return '健康计划与穴位'
  return '测试结果'
})

const showOverview = computed(() => props.mode === 'full')
const showAnalysis = computed(() => props.mode === 'full' || props.mode === 'analysis')
const showRegimen = computed(() => props.mode === 'full' || props.mode === 'plans')
const showPlans = computed(() => props.mode === 'full' || props.mode === 'plans')
const showFooterActions = computed(() => props.mode === 'full')
const showSectionActions = computed(() => props.actionsPlacement !== 'top')

const parsedAiSuggestion = computed(() => {
  let text = ''
  if (props.testResult?.healthSuggestion) {
    text = props.testResult.healthSuggestion.trim()
  }
  // 若存在最近一次药膳文本，叠加展示
  if (!props.isAiLoading && props.latestRecipeText) {
    const baseText = text || (props.testResult?.healthSuggestion || '').trim()
    let baseParsed = baseText ? parseAiHealthSuggestion(baseText) : {}
    baseParsed = baseParsed || {}
    baseParsed.recipeText = props.latestRecipeText
    return baseParsed
  }
  if (!text) return null
  const parsed = parseAiHealthSuggestion(text)
  if (parsed) return parsed
  const plans = []
  const planRegex = /\{[^{}]*"planType"\s*:\s*"([^"]+)"[^{}]*\}/g
  const planMatches = text.match(planRegex)
  if (planMatches && planMatches.length > 0) {
    for (const matchStr of planMatches) {
      try {
        let pStr = matchStr.replace(/,\s*}/g, '}')
        if ((pStr.match(/"/g) || []).length % 2 !== 0) pStr += '"'
        if (!pStr.endsWith('}')) pStr += '}'
        const planObj = JSON.parse(pStr)
        if (planObj.planType) plans.push(planObj)
      } catch {
        const typeMatch = matchStr.match(/"planType"\s*:\s*"([^"]+)"/)
        const nameMatch = matchStr.match(/"planName"\s*:\s*"([^"]+)"/)
        const descMatch = matchStr.match(/"description"\s*:\s*"([^"]+)"/)
        if (typeMatch) {
          plans.push({
            planType: typeMatch[1],
            planName: nameMatch ? nameMatch[1] : '健康计划',
            description: descMatch ? descMatch[1] : '',
            frequency: 'DAILY',
            duration: 30
          })
        }
      }
    }
  }
  if (plans.length > 0) {
    return { plans, summary: '', analysis: '' }
  }
  return null
})

const hasAiAnalysis = computed(() => {
  // 外部可强制指示已生成，避免解析失败时误判为“未生成”
  if (typeof props.analysisReady === 'boolean') {
    return props.analysisReady
  }
  const a = parsedAiSuggestion.value?.analysis
  return !!(a && String(a).length >= 40)
})

const hasAiPlans = computed(() => {
  if (typeof props.plansReady === 'boolean') {
    return props.plansReady
  }
  const p = parsedAiSuggestion.value?.plans
  return Array.isArray(p) && p.length >= 1
})

// 药膳列表生成状态：优先使用外部传入的 recipeReady，其次根据未入库药膳数组判断
const hasAiRecipe = computed(() => {
  if (typeof props.recipeReady === 'boolean') {
    return props.recipeReady
  }
  return Array.isArray(props.unpersistedRecipes) && props.unpersistedRecipes.length > 0
})

const showStreamingPreview = computed(() => {
  return false
})

const computedAcupoints = computed(() =>
  normalizeAcupointsList(parsedAiSuggestion.value?.acupoints)
)

/**
 * 结构化结果“出现动画”：避免生成完成后内容一次性跳出过于突兀
 * - 深度分析/总体原则：逐字打字
 * - 饮食/起居/穴位：逐条出现
 */
const enableRevealAnimation = computed(() => props.variant === 'workspace')
const animatedAnalysisText = ref('')
const animatedSummaryText = ref('')
const displayDietRecommend = ref([])
const displayDietAvoid = ref([])
const displayLifestyle = ref([])
const displayAcupoints = ref([])
const displayedPlans = ref([])
const displayedUnpersistedRecipes = ref([])
let revealToken = 0

// 动画阶段：仅在该阶段渲染“缓冲数组”，否则回落到真实数据源，避免内容丢失/竞态导致空白
const revealingPhase = ref(null) // 'analysis' | 'plans' | 'recipe' | null

const dietRecommendToRender = computed(() => {
  if (!enableRevealAnimation.value) return Array.isArray(parsedAiSuggestion.value?.diet?.recommend) ? parsedAiSuggestion.value.diet.recommend : []
  if (revealingPhase.value === 'plans') return Array.isArray(displayDietRecommend.value) ? displayDietRecommend.value : []
  return Array.isArray(parsedAiSuggestion.value?.diet?.recommend) ? parsedAiSuggestion.value.diet.recommend : []
})

const dietAvoidToRender = computed(() => {
  if (!enableRevealAnimation.value) return Array.isArray(parsedAiSuggestion.value?.diet?.avoid) ? parsedAiSuggestion.value.diet.avoid : []
  if (revealingPhase.value === 'plans') return Array.isArray(displayDietAvoid.value) ? displayDietAvoid.value : []
  return Array.isArray(parsedAiSuggestion.value?.diet?.avoid) ? parsedAiSuggestion.value.diet.avoid : []
})

const lifestyleToRender = computed(() => {
  if (!enableRevealAnimation.value) return Array.isArray(parsedAiSuggestion.value?.lifestyle) ? parsedAiSuggestion.value.lifestyle : []
  if (revealingPhase.value === 'plans') return Array.isArray(displayLifestyle.value) ? displayLifestyle.value : []
  return Array.isArray(parsedAiSuggestion.value?.lifestyle) ? parsedAiSuggestion.value.lifestyle : []
})

const acupointsToRender = computed(() => {
  if (!enableRevealAnimation.value) return normalizeAcupointsList(parsedAiSuggestion.value?.acupoints)
  if (revealingPhase.value === 'plans') return Array.isArray(displayAcupoints.value) ? displayAcupoints.value : []
  return normalizeAcupointsList(parsedAiSuggestion.value?.acupoints)
})

const showDietSection = computed(() => {
  if (!showRegimen.value) return false
  if (!enableRevealAnimation.value) return !!parsedAiSuggestion.value?.diet
  return dietRecommendToRender.value.length > 0 || dietAvoidToRender.value.length > 0
})

const showLifestyleSection = computed(() => {
  if (!showRegimen.value) return false
  if (!enableRevealAnimation.value) {
    return Array.isArray(parsedAiSuggestion.value?.lifestyle) && parsedAiSuggestion.value.lifestyle.length > 0
  }
  return lifestyleToRender.value.length > 0
})

const showAcupointsSection = computed(() => {
  if (!showRegimen.value) return false
  if (!enableRevealAnimation.value) return acupointsToRender.value.length > 0
  return acupointsToRender.value.length > 0
})

const plansToRender = computed(() => {
  if (!enableRevealAnimation.value) return Array.isArray(parsedAiSuggestion.value?.plans) ? parsedAiSuggestion.value.plans : []
  if (revealingPhase.value === 'plans') return Array.isArray(displayedPlans.value) ? displayedPlans.value : []
  return Array.isArray(parsedAiSuggestion.value?.plans) ? parsedAiSuggestion.value.plans : []
})

const recipesToRender = computed(() => {
  const src = Array.isArray(props.unpersistedRecipes) ? props.unpersistedRecipes : []
  if (!enableRevealAnimation.value) return src
  if (revealingPhase.value === 'recipe') return Array.isArray(displayedUnpersistedRecipes.value) ? displayedUnpersistedRecipes.value : []
  return src
})

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
async function typewrite(text, setter, token, speed = 14) {
  const src = String(text || '')
  setter('')
  if (!src) return
  for (let i = 0; i < src.length; i++) {
    if (token !== revealToken) return
    setter(src.slice(0, i + 1))
    // 标点/换行稍微慢一点，更自然
    const ch = src[i]
    const extra = /[，。；！？、\n]/.test(ch) ? 60 : 0
    await sleep(speed + extra)
  }
}

async function revealList(list, targetRef, token, stepDelay = 120) {
  targetRef.value = []
  const arr = Array.isArray(list) ? list : []
  for (const it of arr) {
    if (token !== revealToken) return
    targetRef.value.push(it)
    await sleep(stepDelay)
  }
}

function resetRevealBuffers() {
  animatedAnalysisText.value = ''
  animatedSummaryText.value = ''
  displayDietRecommend.value = []
  displayDietAvoid.value = []
  displayLifestyle.value = []
  displayAcupoints.value = []
}

function fillRevealBuffersInstant(parsed) {
  animatedAnalysisText.value = String(parsed?.analysis || '')
  animatedSummaryText.value = String(parsed?.summary || '')
  displayDietRecommend.value = Array.isArray(parsed?.diet?.recommend) ? parsed.diet.recommend : []
  displayDietAvoid.value = Array.isArray(parsed?.diet?.avoid) ? parsed.diet.avoid : []
  displayLifestyle.value = Array.isArray(parsed?.lifestyle) ? parsed.lifestyle : []
  displayAcupoints.value = normalizeAcupointsList(parsed?.acupoints)
  displayedPlans.value = Array.isArray(parsed?.plans) ? parsed.plans : []
  displayedUnpersistedRecipes.value = Array.isArray(props.unpersistedRecipes) ? props.unpersistedRecipes : []
}

// 按阶段播放动画：analysis/plans/recipe。普通返回页面只做“即时回填”不播放动画。
const prevLoading = ref(false)
const lastStartPhase = ref(null)
const completedPhase = ref(null)

watch(
  () => [props.isAiLoading, props.streamPhase],
  ([loading, phase]) => {
    if (loading === true) {
      prevLoading.value = true
      lastStartPhase.value = phase || null
      return
    }
    // loading: true -> false
    if (prevLoading.value === true && loading === false) {
      completedPhase.value = lastStartPhase.value || null
      prevLoading.value = false
      lastStartPhase.value = null
    }
  },
  { immediate: true }
)

// 正常渲染时：保持缓冲与真实数据一致（用于返回页面时“直接展示完整内容”）
watch(
  () => [parsedAiSuggestion.value, props.unpersistedRecipes],
  ([parsed]) => {
    if (!enableRevealAnimation.value) return
    if (revealingPhase.value) return
    if (parsed) fillRevealBuffersInstant(parsed)
    else displayedUnpersistedRecipes.value = Array.isArray(props.unpersistedRecipes) ? props.unpersistedRecipes : []
  },
  { deep: false, immediate: true }
)

async function waitFor(predicate, { timeoutMs = 4000, intervalMs = 50 } = {}) {
  const start = Date.now()
  // eslint-disable-next-line no-constant-condition
  while (true) {
    if (predicate()) return true
    if (Date.now() - start >= timeoutMs) return false
    await sleep(intervalMs)
  }
}

watch(
  () => completedPhase.value,
  async (phase) => {
    if (!enableRevealAnimation.value) {
      completedPhase.value = null
      return
    }
    if (!phase) return

    const token = ++revealToken
    completedPhase.value = null

    if (phase === 'analysis') {
      // 等待分析文本到位再打字；其余内容不依赖动画，交给 computed 回落展示
      await waitFor(() => !!parsedAiSuggestion.value && !!String(parsedAiSuggestion.value?.analysis || '').trim())
      if (token !== revealToken) return
      revealingPhase.value = 'analysis'
      animatedAnalysisText.value = ''
      animatedSummaryText.value = ''
      const parsed = parsedAiSuggestion.value || {}
      await typewrite(parsed.analysis || '', (v) => (animatedAnalysisText.value = v), token)
      await sleep(180)
      await typewrite(parsed.summary || '', (v) => (animatedSummaryText.value = v), token, 12)
      if (token !== revealToken) return
      // 打字结束后立即同步缓冲，确保图1这类“调养信息不全”不会再发生
      fillRevealBuffersInstant(parsedAiSuggestion.value || {})
      revealingPhase.value = null
      return
    }

    if (phase === 'plans') {
      await waitFor(() => Array.isArray(parsedAiSuggestion.value?.plans) && parsedAiSuggestion.value.plans.length > 0)
      if (token !== revealToken) return
      revealingPhase.value = 'plans'
      const parsed = parsedAiSuggestion.value || {}
      displayDietRecommend.value = []
      displayDietAvoid.value = []
      displayLifestyle.value = []
      displayAcupoints.value = []
      displayedPlans.value = []
      await revealList(parsed?.diet?.recommend, displayDietRecommend, token, 110)
      await revealList(parsed?.diet?.avoid, displayDietAvoid, token, 110)
      await revealList(parsed?.lifestyle, displayLifestyle, token, 140)
      await revealList(normalizeAcupointsList(parsed?.acupoints), displayAcupoints, token, 160)
      await revealList(Array.isArray(parsed?.plans) ? parsed.plans : [], displayedPlans, token, 120)
      if (token !== revealToken) return
      fillRevealBuffersInstant(parsedAiSuggestion.value || {})
      revealingPhase.value = null
      return
    }

    if (phase === 'recipe') {
      await waitFor(() => Array.isArray(props.unpersistedRecipes) && props.unpersistedRecipes.length > 0)
      if (token !== revealToken) return
      revealingPhase.value = 'recipe'
      displayedUnpersistedRecipes.value = []
      await revealList(Array.isArray(props.unpersistedRecipes) ? props.unpersistedRecipes : [], displayedUnpersistedRecipes, token, 130)
      if (token !== revealToken) return
      displayedUnpersistedRecipes.value = Array.isArray(props.unpersistedRecipes) ? props.unpersistedRecipes : []
      revealingPhase.value = null
    }
  },
  { immediate: true }
)

async function runOfflineWorkspaceReplay() {
  if (!enableRevealAnimation.value) return
  const parsed = parsedAiSuggestion.value
  if (!parsed) return

  const token = ++revealToken
  resetRevealBuffers()

  const analysisStr = String(parsed.analysis || '').trim()
  if (analysisStr.length >= 10) {
    revealingPhase.value = 'analysis'
    animatedAnalysisText.value = ''
    animatedSummaryText.value = ''
    await typewrite(analysisStr, (v) => (animatedAnalysisText.value = v), token)
    await sleep(180)
    await typewrite(String(parsed.summary || ''), (v) => (animatedSummaryText.value = v), token, 12)
    if (token !== revealToken) return
    fillRevealBuffersInstant(parsedAiSuggestion.value || {})
    revealingPhase.value = null
  }

  if (token !== revealToken) return
  const latestParsed = parsedAiSuggestion.value || {}
  if (Array.isArray(latestParsed.plans) && latestParsed.plans.length > 0) {
    revealingPhase.value = 'plans'
    displayDietRecommend.value = []
    displayDietAvoid.value = []
    displayLifestyle.value = []
    displayAcupoints.value = []
    displayedPlans.value = []
    await revealList(latestParsed?.diet?.recommend, displayDietRecommend, token, 110)
    await revealList(latestParsed?.diet?.avoid, displayDietAvoid, token, 110)
    await revealList(latestParsed?.lifestyle, displayLifestyle, token, 140)
    await revealList(normalizeAcupointsList(latestParsed?.acupoints), displayAcupoints, token, 160)
    await revealList(Array.isArray(latestParsed?.plans) ? latestParsed.plans : [], displayedPlans, token, 120)
    if (token !== revealToken) return
    fillRevealBuffersInstant(parsedAiSuggestion.value || {})
    revealingPhase.value = null
  }

  if (token !== revealToken) return
  const ur = Array.isArray(props.unpersistedRecipes) ? props.unpersistedRecipes : []
  if (ur.length > 0) {
    revealingPhase.value = 'recipe'
    displayedUnpersistedRecipes.value = []
    await revealList(ur, displayedUnpersistedRecipes, token, 130)
    if (token !== revealToken) return
    displayedUnpersistedRecipes.value = ur
    revealingPhase.value = null
  }

  fillRevealBuffersInstant(parsedAiSuggestion.value || {})
}

watch(
  () => props.revealReplayNonce,
  (n, o) => {
    if (!n || n === o) return
    if (props.isAiLoading) return
    void runOfflineWorkspaceReplay()
  }
)

onBeforeUnmount(() => {
  revealToken++
})

/**
 * 格式化生成内容（当前版本返回空字符串）
 */
const formattedStreamingContent = computed(() => {
  return ''
})

const emit = defineEmits([
  'reset',
  'view-history',
  'generate-analysis',
  'generate-plans',
  'recipe-saved',
  'batch-recipes-saved'
])

/**
 * 格式化分数
 * @param {number|null|undefined} score - 分数
 * @returns {string} 格式化后的分数
 */
const formatScore = (score) => {
  if (score == null || score === undefined) {
    return '0.0'
  }
  return Number(score).toFixed(1)
}

const handleReset = () => {
  emit('reset')
}

const handleViewHistory = () => {
  emit('view-history')
}

// 计划保存相关
const savingPlans = ref(false)
const plansSaved = ref(false)
const savingRecipe = ref(false)
const savingBatchRecipes = ref(false)
const userStore = useUserStore()

  // 格式化频率显示
  const formatFrequency = (freq) => {
    const map = {
      'DAILY': '每天',
      'WEEKLY': '每周',
      'MONTHLY': '每月'
    }
    if (!freq) return ''
    if (/[\u4e00-\u9fa5]/.test(freq)) return freq
    return map[freq.toUpperCase()] || freq
  }

  // 获取计划日期范围
  const getPlanDateRange = (duration) => {
    const start = new Date()
    const end = new Date()
    end.setDate(end.getDate() + (duration || 30))
    
    const formatDate = (d) => {
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }
    
    return `${formatDate(start)} ~ ${formatDate(end)}`
  }

  const getPlanTypeTag = (type) => {
  const map = {
    'DIET': 'success',
    'EXERCISE': 'warning',
    'ACUPOINT': 'danger',
    'SLEEP': 'info'
  }
  return map[type] || ''
}

// 获取计划类型名称
const getPlanTypeLabel = (type) => {
  const map = {
    'DIET': '饮食',
    'EXERCISE': '运动',
    'ACUPOINT': '穴位',
    'SLEEP': '起居'
  }
  return map[type] || '其他'
}

// 保存计划
const handleSavePlans = async () => {
  if (!parsedAiSuggestion.value?.plans?.length) return
  
  savingPlans.value = true
  try {
    let successCount = 0
    const plans = parsedAiSuggestion.value.plans
    
    // 串行保存，避免并发过高
    for (const plan of plans) {
      const planData = {
        userId: userStore.userInfo?.id,
        testId: props.testResult?.id,
        planName: plan.planName || plan.name, // 兼容旧字段
        planType: plan.planType || plan.type, // 兼容旧字段
        description: plan.description,
        targetContent: plan.targetContent || plan.description,
        frequency: plan.frequency,
        startDate: new Date().toISOString().split('T')[0],
        endDate: new Date(Date.now() + (plan.duration || 30) * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        targetCount: plan.duration || 30,
        status: 1 // 进行中
      }
      
      const res = await createHealthPlan(planData)
      if (res.code === 200) successCount++
    }
    
    if (successCount > 0) {
      plansSaved.value = true
      ElMessage.success(`成功保存 ${successCount} 个健康计划，请前往“健康计划”页面查看`)
    } else {
      console.warn('plans保存数量为0')
      ElMessage.warning('保存计划失败，请稍后重试')
    }
  } catch (error) {
    console.error('保存计划失败', error)
    if (error.response) {
      console.error('API响应详情:', error.response)
    }
    ElMessage.error('保存计划时发生异常')
  } finally {
    savingPlans.value = false
  }
}

// 保存“药膳建议”为药膳
const handleSaveRecipeSuggestion = async () => {
  if (!parsedAiSuggestion.value?.recipeText) return
  savingRecipe.value = true
  try {
    const res = await saveRecipeFromSuggestion({ testId: props.testResult?.id, text: parsedAiSuggestion.value.recipeText })
    if (res?.code === 200 && res?.data) {
      // 保存后同时加入收藏，确保“药膳列表/收藏页”都可见
      try {
        if (res.data?.id != null) await favoriteRecipe(res.data.id)
      } catch (_) {
        // 收藏失败不影响“已入库”
      }
      ElMessage.success('药膳已保存并收藏')
      // 通知父组件更新“最近一次已入库的药膳”
      emit('recipe-saved', res.data)
    } else {
      ElMessage.error(res?.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存异常')
  } finally {
    savingRecipe.value = false
  }
}

/**
 * 一键保存“批量未入库”的药膳：逐条调用 /api/recipe/save
 * 注意：前端批量结构是统一 normalize 后的字段，需要转换成后端 saveGeneratedRecipeJson 支持的 JSON 结构。
 */
const handleSaveBatchRecipes = async () => {
  const list = Array.isArray(props.unpersistedRecipes) ? props.unpersistedRecipes : []
  if (!list.length) return

  savingBatchRecipes.value = true
  try {
    // 为了支持“重复点击”，这里在入库前先查出本次 testId 下已存在的药膳名，存在则跳过并提示。
    const testId = props.testResult?.id
    const existedNames = new Set()
    if (testId) {
      try {
        const rr = await getRecipesByTestId(testId)
        if (rr?.code === 200 && Array.isArray(rr.data)) {
          rr.data.forEach(r => {
            const n = String(r?.recipeName || '').trim()
            if (n) existedNames.add(n)
          })
        }
      } catch (e) {
        // 查重失败不阻止保存，但会失去“已存在提示”的准确性
        console.warn('[BatchSaveRecipe] 获取已存在药膳失败，继续保存', e)
      }
    }

    let successCount = 0
    let existedCount = 0
    let lastSaved = null
    let favoriteCount = 0

    for (const rec of list) {
      const recipeName = rec?.recipeName || rec?.name || 'AI药膳'
      const normalizedRecipeName = String(recipeName).trim()
      if (existedNames.has(normalizedRecipeName)) {
        existedCount++
        continue
      }

      const constitutionType = rec?.constitutionType || props.testResult?.primaryConstitution || 'ALL'
      const ingredients = Array.isArray(rec?.ingredients)
        ? rec.ingredients.map(i => ({
            name: i?.name || '',
            amount: i?.amount || '',
            unit: i?.unit || '',
            note: i?.note || i?.remark || ''
          }))
        : []
      const steps = Array.isArray(rec?.steps) ? rec.steps : []
      const contraindicationsText = Array.isArray(rec?.contraindications)
        ? rec.contraindications.join('、')
        : (rec?.contraindicationsText || rec?.contraindications || '')
      const nutritionInfo = rec?.nutritionInfo || rec?.nutrition || {}

      const payload = {
        recipeName,
        constitutionType,
        season: rec?.season || 'ALL',
        category: rec?.category || '',
        difficulty: Number.isFinite(Number(rec?.difficulty)) ? Number(rec.difficulty) : null,
        cookingTime: Number.isFinite(Number(rec?.cookingTime)) ? Number(rec.cookingTime) : null,
        servings: Number.isFinite(Number(rec?.servings)) ? Number(rec.servings) : null,
        ingredients,
        steps,
        efficacy: rec?.efficacy || '',
        suitableSymptoms: rec?.suitableSymptoms || '',
        contraindications: contraindicationsText,
        nutritionInfo,
        tips: rec?.tips || ''
      }
      const required = ['recipeName', 'constitutionType', 'season', 'category', 'difficulty', 'cookingTime', 'servings']
      const missing = required.filter(k => payload[k] === null || payload[k] === undefined || payload[k] === '')
      if (missing.length) {
        console.warn(`[BatchSaveRecipe] 字段缺失(${recipeName}): ${missing.join(',')}`)
      }

      const res = await saveRecipeFromJson(payload)
      if (res?.code === 200 && res?.data) {
        successCount++
        lastSaved = res.data
        existedNames.add(normalizedRecipeName)
        // 保存后自动收藏
        try {
          if (res.data?.id != null) {
            await favoriteRecipe(res.data.id)
            favoriteCount++
          }
        } catch (_) {
          // ignore
        }
      }
    }

    if (successCount > 0) {
      ElMessage.success(
        `成功保存 ${successCount} 个药膳${favoriteCount > 0 ? `（已自动收藏 ${favoriteCount} 个）` : ''}${existedCount > 0 ? `（已存在 ${existedCount} 个，已跳过）` : ''}`
      )
      if (lastSaved) emit('recipe-saved', lastSaved)
      emit('batch-recipes-saved', { count: successCount, lastSaved })
    } else if (existedCount > 0) {
      ElMessage.info(`药膳已存在：已跳过 ${existedCount} 个药膳，无需重复保存`)
    } else {
      ElMessage.warning('保存失败：未保存到任何药膳')
    }
  } catch (e) {
    console.error('一键保存药膳失败', e)
    ElMessage.error('保存异常：请稍后重试')
  } finally {
    savingBatchRecipes.value = false
  }
}
</script>

<style scoped lang="scss">
.item-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .title-left {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .title-actions {
    flex-shrink: 0;
  }
}

.structured-ai-suggestion {
  .sub-title {
    font-weight: bold;
    color: #409EFF;
    margin-bottom: 8px;
    margin-top: 16px;
    font-size: 15px;
    
    &:first-child {
      margin-top: 0;
    }
  }
  
  p {
    line-height: 1.8;
    color: #606266;
    margin: 0;
  }
  
  .diet-content {
    background: #fdf6ec;
    padding: 12px;
    border-radius: 6px;
    
    .label {
      font-weight: bold;
      margin-right: 4px;
      
      &.suitable { color: #67C23A; }
      &.avoid { color: #F56C6C; }
    }
    
    .diet-item {
      color: #606266;
      font-size: 14px;
    }
  }
  
  .simple-list {
    padding-left: 20px;
    margin: 0;
    color: #606266;
    line-height: 1.8;
    
    li {
      margin-bottom: 4px;
    }
  }

  .acupoints-box {
    margin-bottom: 16px;
  }

  .structured-acupoints {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 12px;
    margin-top: 12px;

    .point-item {
      background: #f0f9eb;
      padding: 12px 14px;
      border-radius: 8px;
      border: 1px solid #e1f3d8;
    }

    .point-name {
      font-weight: 600;
      color: #67c23a;
      margin-bottom: 8px;
      font-size: 14px;
    }

    .point-detail p {
      margin: 4px 0;
      font-size: 13px;
      line-height: 1.55;
      color: #606266;
    }
  }
}

/* 计划卡片样式（用于整行“推荐健康计划”模块） */
.plans-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.plan-card {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 18px rgba(15, 23, 42, 0.10);
  }
}

.plan-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.plan-card-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.35;
  flex: 1;
  min-width: 0;
}

.plan-card-desc {
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.plan-card-info {
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;

  .info-row {
    display: flex;
    align-items: flex-start;
    font-size: 12px;
    margin-bottom: 8px;
    line-height: 1.45;

    &:last-child {
      margin-bottom: 0;
    }

    .el-icon {
      margin-top: 2px;
      margin-right: 6px;
      font-size: 14px;
      flex-shrink: 0;
    }

    &.target {
      color: #409EFF;
      .el-icon { color: #409EFF; }
    }

    &.date,
    &.freq {
      color: #64748b;
    }

    .label {
      margin-right: 4px;
      white-space: nowrap;
      color: #475569;
      font-weight: 600;
    }
  }
}

.plan-card-progress {
  margin-bottom: 12px;

  .progress-labels {
    display: flex;
    justify-content: flex-end;
    font-size: 12px;
    color: #94a3b8;
    margin-top: 4px;
  }
}

.ai-streaming-content {
  background: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid #409eff;
  white-space: pre-wrap;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #334155;
  line-height: 1.8;
  position: relative;
}

.ai-report-box {
  max-height: 520px;
  overflow: auto;
}

.streaming-text {
  font-size: 13px;
}

.ws-reveal-item {
  animation: wsFadeUp 260ms ease-out both;
}

.ws-reveal-card {
  animation: wsFadeUp 320ms ease-out both;
}

@keyframes wsFadeUp {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.cursor {
  display: inline-block;
  width: 2px;
  height: 1.2em;
  background-color: #409eff;
  margin-left: 4px;
  animation: blink 1s infinite;
  vertical-align: middle;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.test-result {
  margin-top: 32px;
  
  .result-card {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    margin-bottom: 24px;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
    
    .card-header {
      padding: 16px;
      border-bottom: 1px solid #e2e8f0;
      background: linear-gradient(135deg, #f0f9ff 0%, #ffffff 100%);
      font-weight: 700;
      font-size: 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  }
  
  .result-content {
    padding: 24px;
  }
  
  .completion-section {
    text-align: center;
    margin-bottom: 14px;
    
    .completion-title {
      color: #0f172a;
      margin-bottom: 6px;
      font-size: 18px;
      font-weight: 700;
      letter-spacing: 0.2px;
    }
    
    .completion-desc {
      color: #64748b;
      font-size: 13px;
    }
  }
  
  .constitution-stats {
    display: grid;
    grid-template-columns: 1.2fr 0.8fr;
    gap: 14px;
    margin-bottom: 16px;
  }

  .main-stat-card {
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.10) 0%, #ffffff 55%);
    border: 1px solid #dcfce7;
    border-radius: 12px;
    padding: 16px;
  }

  .main-stat-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
  }

  .main-stat-title {
    font-size: 13px;
    color: #64748b;
    font-weight: 600;
  }

  .main-stat-name {
    font-size: 22px;
    font-weight: 800;
    color: #065f46;
    margin-bottom: 10px;
    letter-spacing: 0.2px;
  }

  .main-stat-score {
    display: flex;
    align-items: baseline;
    gap: 6px;

    .label {
      font-size: 12px;
      color: #64748b;
    }

    .value {
      font-size: 28px;
      font-weight: 800;
      color: #10b981;
      line-height: 1;
    }

    .unit {
      font-size: 12px;
      color: #64748b;
    }
  }

  .side-stat-cards {
    display: grid;
    grid-template-rows: 1fr 1fr;
    gap: 14px;
  }

  .side-stat-card {
    background: #ffffff;
    border: 1px solid #eef2f7;
    border-radius: 12px;
    padding: 14px;
  }

  .side-stat-title {
    font-size: 13px;
    color: #64748b;
    font-weight: 600;
    margin-bottom: 8px;
  }

  .side-stat-value {
    display: flex;
    align-items: baseline;
    gap: 6px;

    .name {
      font-weight: 700;
      color: #0f172a;
      font-size: 14px;
    }

    .score {
      color: #64748b;
      font-size: 12px;
    }
  }

  .side-stat-empty {
    color: #94a3b8;
    font-size: 13px;
    line-height: 1.5;
  }

  .side-stat-tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .side-stat-hint {
    margin-top: 8px;
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.5;
  }

  @media (max-width: 992px) {
    .constitution-stats {
      grid-template-columns: 1fr;
    }

    .side-stat-cards {
      grid-template-rows: none;
      grid-template-columns: 1fr;
    }
  }
  
  .analysis-section {
    margin-top: 12px;
    padding: 0;
    background: transparent;
    border-radius: 0;

    .analysis-grid {
      display: grid;
      grid-template-columns: 1.1fr 0.9fr;
      gap: 16px;
      align-items: start;
    }

    .analysis-col {
      min-width: 0;
    }

    .analysis-item {
      background: #ffffff;
      border: 1px solid #eef2f7;
      border-radius: 10px;
      padding: 14px;
      margin-bottom: 0;
    }

    .item-title.compact {
      padding-bottom: 8px;
      border-bottom: 1px dashed #e2e8f0;
      margin-bottom: 10px;
    }

    /* 在中屏以下（<=1200）就切换为单列，避免拥挤 */
    @media (max-width: 1200px) {
      .analysis-grid {
        grid-template-columns: 1fr;
      }
    }

    .analysis-paragraph {
      white-space: pre-wrap;
      line-height: 1.8;
      margin: 0;
      color: #606266;
    }
    
    .suggestions-list {
      line-height: 2;
      padding-left: 20px;
      color: #475569;
      font-size: 14px;

      li {
        margin-bottom: 8px;

        &:last-child {
          margin-bottom: 0;
        }
      }
    }
  }

  .plans-section {
    margin-top: 16px;
    background: #ffffff;
    border: 1px solid #eef2f7;
    border-radius: 10px;
    padding: 14px;
  }

  .plans-section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    padding-bottom: 10px;
    border-bottom: 1px dashed #e2e8f0;
    margin-bottom: 12px;
  }

  .plans-section-title {
    font-weight: 700;
    color: #0f172a;
  }

  .plans-title-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    align-items: center;
  }

  .plans-empty {
    margin-top: 12px;
  }
  
  .actions-section {
    margin-top: 24px;
    text-align: center;
    display: flex;
    gap: 12px;
    justify-content: center;
  }
}

.test-result.embedded {
  margin-top: 0;
}

.embedded-content {
  padding: 0;
}

/* 工作台变体：更像“AI 主舞台 + 结构化卡片” */
.test-result.variant-workspace {
  .analysis-section {
    .analysis-item {
      border-radius: 12px;
    }
  }

  .workspace-section {
    margin-top: 12px;
  }

  .workspace-cards {
    margin-top: 14px;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;

    @media (max-width: 992px) {
      grid-template-columns: 1fr;
    }
  }

  .ws-card {
    background: #ffffff;
    border: 1px solid #eef2f7;
    /* 与“推荐健康计划”边框保持一致 */
    border-radius: 10px;
    padding: 14px;
  }

  .ws-card-wide {
    grid-column: 1 / -1;
  }

  .ws-batch-recipes-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .ws-batch-recipes-actions {
    flex-shrink: 0;
  }

  .ws-batch-recipes-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;

    @media (max-width: 992px) {
      grid-template-columns: 1fr;
    }
  }

  /* 内层“每道药膳卡片”在大容器里需要更紧凑一些 */
  .ws-batch-recipes-grid .ws-batch-recipe-card {
    padding: 12px;
  }

  .ws-batch-recipes-grid .ws-card-title {
    font-size: 12px;
    margin-bottom: 8px;
  }

  .ws-card-title {
    font-weight: 800;
    color: #0f172a;
    font-size: 13px;
    margin-bottom: 10px;
  }

  .ws-card-body {
    color: #334155;
    font-size: 13px;
    line-height: 1.85;
  }

  /* workspace 下“饮食宜忌”与报告区保持同款视觉 */
  .diet-content {
    background: #fdf6ec;
    padding: 12px;
    border-radius: 6px;
  }

  .diet-content .label {
    font-weight: bold;
    margin-right: 4px;
  }

  .diet-content .label.suitable {
    color: #67c23a;
  }

  .diet-content .label.avoid {
    color: #f56c6c;
  }

  .diet-content .diet-item {
    color: #606266;
    font-size: 14px;
  }

  .ws-card-placeholder {
    /* 与“推荐健康计划”边框保持一致（不使用虚线占位） */
    border-style: solid;
    border-color: #eef2f7;
    background: #ffffff;
  }

  .ws-card-placeholder-body {
    color: #94a3b8;
    font-weight: 700;
    letter-spacing: 0.2px;
  }

  .ws-recipe-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  @media (max-width: 992px) {
    .ws-recipe-grid {
      grid-template-columns: 1fr;
    }
  }

  .ws-recipe-card {
    border: 1px solid #eef2f7;
    border-radius: 10px;
    padding: 12px;
    background: #ffffff;
  }

  .ws-recipe-name {
    font-weight: 800;
    color: #0f172a;
    margin-bottom: 6px;
  }

  /* 穴位卡片网格（对齐健康计划卡片风格） */
  .ws-acupoint-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  @media (min-width: 1400px) {
    .ws-acupoint-grid {
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }
  }

  @media (max-width: 992px) {
    .ws-acupoint-grid {
      grid-template-columns: 1fr;
    }
  }

  .ws-acupoint-card {
    border: 1px solid #eef2f7;
    background: #ffffff;
    border-radius: 12px;
    padding: 12px;
    box-shadow: 0 1px 0 rgba(15, 23, 42, 0.02);
    transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 18px rgba(15, 23, 42, 0.10);
      border-color: rgba(59, 130, 246, 0.25);
    }
  }

  .ws-acupoint-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    min-width: 0;
  }

  .ws-acupoint-badge {
    font-size: 12px;
    font-weight: 800;
    color: #1d4ed8;
    background: rgba(59, 130, 246, 0.10);
    border: 1px solid rgba(59, 130, 246, 0.22);
    padding: 2px 8px;
    border-radius: 999px;
    flex-shrink: 0;
  }

  .ws-acupoint-name {
    font-size: 14px;
    font-weight: 900;
    color: #0f172a;
    line-height: 1.2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ws-acupoint-meta {
    background: #f8fafc;
    border: 1px solid #eef2f7;
    border-radius: 10px;
    padding: 10px;
  }

  .ws-acupoint-row {
    display: grid;
    grid-template-columns: 64px 1fr;
    gap: 8px;
    font-size: 12px;
    line-height: 1.6;
    color: #334155;
    margin-bottom: 8px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .ws-acupoint-row .k {
    color: #475569;
    font-weight: 800;
    white-space: nowrap;
  }

  .ws-acupoint-row .v {
    color: #334155;
  }

  .workspace-cards-empty {
    margin-top: 14px;
  }

  /* 生成主舞台（深色阅读区） */
  .ai-streaming-content {
    background: radial-gradient(1200px 500px at 20% 0%, rgba(59, 130, 246, 0.25) 0%, rgba(15, 23, 42, 0.92) 55%, rgba(2, 6, 23, 0.98) 100%);
    border: 1px solid rgba(148, 163, 184, 0.18);
    border-left: 0;
    border-radius: 12px;
    padding: 18px 18px 16px;
    color: #e2e8f0;
    min-height: 360px;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  }

  .ai-report-box {
    max-height: 520px;
    overflow: auto;
  }

  .streaming-text {
    font-size: 14px;
    line-height: 1.95;
    letter-spacing: 0.1px;
    color: #e2e8f0;
  }

  .cursor {
    background-color: #38bdf8;
  }

  /* 让概览标题更像“报告抬头” */
  .completion-section {
    margin: 6px 0 12px;

    .completion-title {
      font-size: 16px;
      font-weight: 900;
      letter-spacing: 0.2px;
    }

    .completion-desc {
      font-size: 12px;
    }
  }

  /* 结构化结果卡片网格在 workspace-cards 中实现 */
}

</style>

