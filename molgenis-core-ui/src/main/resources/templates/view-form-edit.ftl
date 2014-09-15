<#include "molgenis-header.ftl">
<#include "molgenis-footer.ftl">
<#import "molgenis-input-elements.ftl" as input>
<#import "form-macros.ftl" as f>

<#assign css=['select2.css', 'bootstrap-datetimepicker.min.css', 'molgenis-form.css']>
<#assign js=['jquery.validate.min.js', 'select2.min.js', 'moment-with-locales.min.js', 'bootstrap-datetimepicker.min.js', 'molgenis-form-edit.js', 'ace/src-min-noconflict/ace.js']>

<@header css js/>

<script>
	var forms = [];
</script>

<div id="success-message" class="form-group" style="display: none">
	<div class="col-md-12">
		<div class="alert alert-success">
			<button type="button" class="close">&times;</button>
			<strong>${form.title} saved.</strong>
		</div>
	</div>
</div>

<#if back??>
	<a href="${back}" class="btn btn-default btn-xs">Back to list</a>
	<hr></hr>
</#if>

<form role="form" class="form-horizontal col-md-12 pull-left" id="entity-form" method="POST" action="/api/v1/${form.metaData.name?lower_case}<#if form.primaryKey??><#if form.primaryKey?is_number>/${form.primaryKey?c}<#else>/${form.primaryKey}</#if></#if>">		

	<div class="form-group">
		<#if form.primaryKey??>
			<input type="hidden" name="_method" value="PUT" >
		<#else>
			<input type="hidden" name="_method" value="" >
		</#if>
	</div>
	
	<div class="col-md-6">	
		<#list form.metaData.fields as field>
			<#if form.entity??>
				<@input.render field form.hasWritePermission form.entity form.metaData.forUpdate/>
			<#else>
				<@input.render field form.hasWritePermission '' form.metaData.forUpdate/>
			</#if>
		</#list>
		
		<#if form.hasWritePermission>
			<div class="form-group">
				<div class="col-md-12">
	  				<button type="submit" class="btn btn-large btn-primary pull-right">Save</button>
				</div>
			</div>
		</#if>
	</div>
</form>

<@f.remoteValidationRules form />

<@footer/>