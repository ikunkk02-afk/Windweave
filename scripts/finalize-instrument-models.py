"""Adapt the four Blockbench exports for Minecraft 1.21.1 and split animated heads."""
from pathlib import Path
from copy import deepcopy
import json

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/windweave'
def save(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

for name in ['anemometer','wind_vane','wind_controller','wind_meter']:
    path=ASSETS/f'models/item/{name}.json'
    model=json.loads(path.read_text(encoding='utf-8'))
    for key in ['format_version','groups','texture_size']:
        model.pop(key,None)
    model['textures']={k:'windweave:block/instruments/'+v.split('/')[-1] for k,v in model['textures'].items() if k!='particle'}
    model['textures']['particle']='windweave:block/instruments/housing'
    model['ambientocclusion']=False
    save(path,model)
    if name=='wind_meter': continue
    base=deepcopy(model)
    base.pop('display',None)
    base['parent']='minecraft:block/block'
    base['ambientocclusion']=True
    moving = {'anemometer':('rotor/','anemometer_rotor'), 'wind_vane':('head/','wind_vane_head')}.get(name)
    if moving:
        prefix,headname=moving
        head=deepcopy(base)
        head['ambientocclusion']=False
        head['elements']=[e for e in model['elements'] if e['name'].startswith(prefix)]
        base['elements']=[e for e in model['elements'] if not e['name'].startswith(prefix)]
        assert head['elements'] and base['elements']
        assert len(head['elements'])+len(base['elements'])==len(model['elements'])
        save(ASSETS/f'models/block/{headname}.json',head)
    save(ASSETS/f'models/block/{name}.json',base)

for path in (ASSETS/'lang').glob('*.json'):
    data=json.loads(path.read_text(encoding='utf-8'))
    data={k:v for k,v in data.items() if not k.startswith('windweave.hud.')}
    save(path,data)
print('Finalized four item models, three block models and two animated head models.')
