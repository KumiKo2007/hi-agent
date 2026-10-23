#!/usr/bin/env python3
"""Prepare NEW fixtures only in the documented isolated full-app database. No private files/tokens reused."""
import json, pathlib, re, subprocess, urllib.request, uuid, sys

BASE='http://127.0.0.1:58088/api'
DATABASE='hi_agent_full_app_runtime'
def api(path, body=None, token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
    with urllib.request.urlopen(request,timeout=20) as response: result=json.load(response)
    if result.get('code')!=200: raise RuntimeError('测试fixture公开HTTP操作失败：'+path)
    return result['data']

def main():
    if len(sys.argv)!=2: raise SystemExit('Usage: pr-review-prepare-fixture.py /absolute/output-fixture.json')
    target=pathlib.Path(sys.argv[1]); assert target.is_absolute()
    token=api('/login',{'account':'runtime-user@example.invalid','password':'RuntimeUser2026Local'})['token']
    owner=api('/users',token=token)['id']
    assert re.fullmatch(r'[A-Za-z0-9-]{1,64}',owner)
    provider=api('/llms/providers',{'protocol':'OPENAI','name':'PR报告外部模型验收-'+uuid.uuid4().hex[:8],
        'config':{'apiKey':'review-local-fixture-key','baseUrl':'http://127.0.0.1:18858/v1'},'status':True},token)
    model=api('/llms/models',{'providerId':provider['id'],'modelId':'pr-review-fixture','name':'PR报告独立验收模型','type':'CHAT'},token)
    if model.get('status') is False: raise RuntimeError('新模型未启用，请通过公开设置启用后重新运行')
    result={'modelId':model['id']}
    service=str(uuid.uuid4())
    action_schema=lambda name: {'type':'object','properties':{'owner':{'type':'string'},'repo':{'type':'string'},**({'pullNumber':{'type':'integer'},'method':{'type':'string'},'page':{'type':'integer'},'perPage':{'type':'integer'}} if name=='pull_request_read' else {'path':{'type':'string'},'sha':{'type':'string'}})},'required':['owner','repo',*(['pullNumber','method'] if name=='pull_request_read' else ['path','sha'])],'additionalProperties':True}
    definitions=[{'name':name,'enabled':True,'parameters':action_schema(name)} for name in ['pull_request_read','get_file_contents']]
    quote=lambda value: "'"+str(value).replace("'","''")+"'"
    statements=["DO $$ BEGIN IF current_database() <> '"+DATABASE+"' THEN RAISE EXCEPTION 'Wrong test database'; END IF; END $$;"]
    statements.append(f"INSERT INTO tools(id,name,user_id,tool_type,upload_type,status) VALUES ({quote(service)},'PR只读fixture',{quote(owner)},'MCP','URL','APPROVED');")
    statements.append(f"INSERT INTO user_tools(id,tool_id,user_id,name,mcp_server_name,tool_list,version) VALUES ({quote(str(uuid.uuid4()))},{quote(service)},{quote(owner)},'PR只读fixture','github',{quote(json.dumps(definitions))},'fixture-v1');")
    for name in ['pull_request_read','get_file_contents']:
        statements.append('INSERT INTO tool_action_policies(service_id,action_name,classification,schema_json) VALUES ('+quote(service)+','+quote(name)+",'READ_ONLY',"+quote(json.dumps(action_schema(name)))+'::jsonb);')
    subprocess.run(['docker','exec','-i','hi-agent-issue25-pg','psql','-U','postgres','-d',DATABASE,'-v','ON_ERROR_STOP=1','-q'],input='\n'.join(statements).encode(),check=True)
    # 仅目录/平台分类是fixture；实际Agent选择/个人加密连接/固定快照全部通过生产HTTP。
    agent=api('/agents',{'name':'PR只读验收-'+uuid.uuid4().hex[:8],'systemPrompt':'只读审查','toolIds':[service]},token)
    if not agent.get('enabled'):
        request=urllib.request.Request(BASE+'/agents/'+agent['id']+'/toggle-status',data=b'{}',headers={'Content-Type':'application/json','Authorization':'Bearer '+token},method='PUT')
        with urllib.request.urlopen(request) as response: assert json.load(response)['code']==200
    request=urllib.request.Request(BASE+'/agents/'+agent['id']+'/tool-services/'+service+'/actions',data=json.dumps({'actionNames':['pull_request_read','get_file_contents']}).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+token},method='PUT')
    with urllib.request.urlopen(request) as response: assert json.load(response)['code']==200
    api('/users/github-connection',{'token':'github_pat_pr_review_fixture_only','repositories':['alice/repo']},token)
    for key,number in [('inputId',3801),('controlId',3802),('failureId',3803)]:
        snapshot=api('/reviews/inputs',{'agentId':agent['id'],'serviceId':service,'pullRequestUrl':'https://github.com/alice/repo/pull/'+str(number)},token)
        result[key]=snapshot['id']
    target.write_text(json.dumps(result,ensure_ascii=False,indent=2))
    print('仅新增独立provider/model与只读目录；生产HTTP创建Agent/个人连接/固定输入，JWT未落盘。')

if __name__=='__main__': main()
