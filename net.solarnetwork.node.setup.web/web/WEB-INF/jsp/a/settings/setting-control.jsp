<%--
	Expects the following request-or-higher properties:
	
	settingsService - the SettingService
	provider - the current provider
	setting - the current setting
	settingId - the ID to use for the setting input
	instanceId - the instance ID to use
--%>
<c:set var="settingValue" scope="page">
	<setup:settingValue service='${settingsService}' provider='${provider}' setting='${setting}'/>
</c:set>
<c:choose>
	<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.KeyedSettingSpecifier')}">
		<div class="control-group" id="cg-${settingId}">
			<label class="control-label" for="${settingId}">
				<setup:message key="${setting.key}.key" messageSource="${provider.messageSource}" text="${setting.key}"/>
			</label>
			<div class="controls">
				<c:choose>
					<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.SliderSettingSpecifier')}">
						<div id="${settingId}" class="setting slider span5"></div>
						<script>
						$(function() {
							SolarNode.Settings.addSlider({
								key: '${settingId}',
								min: '${setting.minimumValue}',
								max: '${setting.maximumValue}',
								step: '${setting.step}',
								value: '${fn:escapeXml(settingValue)}',
								xint: '${setting["transient"]}',
								provider: '${provider.settingUID}',
								setting: '${setup:js(setting.key)}',
								instance: '${instanceId}'
							});
						});
						</script>
					</c:when>
					<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.ToggleSettingSpecifier')}">
					    <button type="button" class="toggle btn<c:if test='${settingValue eq  setting.trueValue}'> btn-success active</c:if>" 
					    	id="${settingId}">
					    	<c:choose>
					    		<c:when test="${settingValue eq  setting.trueValue}">
					    			<fmt:message key="settings.toggle.on"/>
					    		</c:when>
					    		<c:otherwise>
					    			<fmt:message key="settings.toggle.off"/>
					    		</c:otherwise>
					    	</c:choose>
					    </button>
						<script>
						$(function() {
							SolarNode.Settings.addToggle({
								key: '${settingId}',
								on: '${setting.trueValue}',
								onLabel: '<fmt:message key="settings.toggle.on"/>',
								off: '${setting.falseValue}',
								offLabel: '<fmt:message key="settings.toggle.off"/>',
								value: '${fn:escapeXml(settingValue)}',
								xint: '${setting["transient"]}',
								provider: '${provider.settingUID}',
								setting: '${setup:js(setting.key)}',
								instance: '${instanceId}'
							});
						});
						</script>
					</c:when>
					<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.RadioGroupSettingSpecifier')}">
						<c:forEach items="${setting.valueTitles}" var="entry">
							<label class="radio inline">
								<input type="radio" name="${settingId}" id="${settingId}" value="${entry.key}"
									<c:if test='${settingValue eq  entry.key}'>checked="checked"</c:if>
									/>
								${entry.value}
							</label>							
							<c:set var="help">
								<setup:message key='${entry.key}.desc' messageSource='${provider.messageSource}'/>
							</c:set>
			
							<c:if test="${fn:length(help) > 0}">
								<button type="button" class=" help-popover help-icon" tabindex="-1"
										data-content="${fn:escapeXml(help)}"
										data-html="true">
									<i class="icon-question-sign"></i>
								</button>
							</c:if>
							<br/>
						</c:forEach>
						<script>
						$(function() {
							SolarNode.Settings.addRadio({
								key: '${settingId}',
								xint: '${setting["transient"]}',
								provider: '${provider.settingUID}',
								setting: '${setup:js(setting.key)}',
								instance: '${instanceId}'
							});
						});
						</script>
					</c:when>
					<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.TextFieldSettingSpecifier')}">
						<input type="text" name="${settingId}" id="${settingId}" class="span5" maxLength="255"
							value="${settingValue}" />
						<script>
						$(function() {
							SolarNode.Settings.addTextField({
								key: '${settingId}',
								xint: '${setting["transient"]}',
								provider: '${provider.settingUID}',
								setting: '${setup:js(setting.key)}',
								instance: '${instanceId}'
							});
						});
						</script>
					</c:when>
					<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.TitleSettingSpecifier')}">
						${fn:escapeXml(settingValue)}
					</c:when>
					<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.LocationLookupSettingSpecifier')}">
						<span id="${settingId}">
							<span class="setting-value">
								<c:if test="${not empty setting.sourceName}">
									<fmt:message key="lookup.selected.item">
										<fmt:param value="${setting.sourceName}"/>
										<fmt:param value="${setting.locationName}"/>
									</fmt:message>
								</c:if>
							</span>
							<button type="button" class="btn">
								<fmt:message key="settings.change"/>
							</button>
						</span>
						<script>
						$(function() {
							SolarNode.Settings.addLocationFinder({
								key: '${settingId}',
								locationType: '${setting.locationTypeKey}',
								sourceName: '${setup:js(setting.sourceName)}',
								locationName: '${setup:js(setting.locationName)}',
								valueLabel: '<fmt:message key="lookup.selected.item"/>',
								value: '${fn:escapeXml(settingValue)}',
								xint: '${setting["transient"]}',
								provider: '${provider.settingUID}',
								setting: '${setup:js(setting.key)}',
								instance: '${instanceId}'
							});
						});
						</script>
					</c:when>
				</c:choose>
				
				<c:set var="help">
					<setup:message key='${setting.key}.desc' messageSource='${provider.messageSource}'/>
				</c:set>

				<c:if test="${fn:length(help) > 0}">
					<button type="button" class=" help-popover help-icon" tabindex="-1"
							data-content="${fn:escapeXml(help)}"
							data-html="true">
						<i class="icon-question-sign"></i>
					</button>
				</c:if>
				
				<span class="help-inline active-value clean"><span class="text-info">
					<fmt:message key="settings.current.value.label"/>:
					<code class="value">
						<c:choose>
							<c:when test="${setup:instanceOf(setting, 'net.solarnetwork.node.settings.ToggleSettingSpecifier')}">
						    	<c:choose>
						    		<c:when test="${settingValue eq  setting.trueValue}">
						    			<fmt:message key="settings.toggle.on"/>
						    		</c:when>
						    		<c:otherwise>
						    			<fmt:message key="settings.toggle.off"/>
						    		</c:otherwise>
						    	</c:choose>
							</c:when>
							<c:otherwise>
								${settingValue}
							</c:otherwise>
						</c:choose>
					</code>
				</span></span>
			</div>
		</div>
	</c:when>
</c:choose>
