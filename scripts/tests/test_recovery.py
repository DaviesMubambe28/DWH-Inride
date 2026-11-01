import importlib.util
from pathlib import Path
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('recovery',Path(__file__).resolve().parents[1]/'service-recovery.py')
recovery=importlib.util.module_from_spec(spec);spec.loader.exec_module(recovery)
class RecoveryTests(unittest.TestCase):
 def test_restore_refuses_same_target_and_unapproved_target(self):
  with self.assertRaises(ValueError): recovery.validate_target('https://same.supabase.co','https://same.supabase.co','same.supabase.co')
  with self.assertRaises(ValueError): recovery.validate_target('https://source.supabase.co','https://target.supabase.co','wrong')
  recovery.validate_target('https://source.supabase.co','https://target.supabase.co','target.supabase.co')
 def test_hash_verification_detects_corrupt_and_escaping_artifacts(self):
  import hashlib
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);(root/'object').write_bytes(b'fixture')
   record={'artifact':'object','sha256':hashlib.sha256(b'fixture').hexdigest(),'bytes':7}
   recovery.verify_object(root,record)
   (root/'object').write_bytes(b'corrupt')
   with self.assertRaises(ValueError):recovery.verify_object(root,record)
   with self.assertRaises(ValueError):recovery.verify_object(root,{**record,'artifact':'../object'})
 def test_paginated_storage_inventory_includes_nested_objects(self):
  def request(path,body=None):
   if body['prefix']=='':return [{'name':'user','id':None}]
   return [{'name':'file.pdf','id':'fixture','metadata':{'mimetype':'application/pdf'}}]
  objects=recovery.list_objects(request,'rider-documents')
  self.assertEqual([o['name'] for o in objects],['user/file.pdf'])
 def test_inventory_never_accepts_traversal_from_remote_names(self):
  for name in ['../escape','bad'+chr(92)+'name','bad'+chr(0)]:
   with self.assertRaises(ValueError):recovery.list_objects(lambda *a:[{'name':name,'id':'file'}],'bucket')
 def test_full_service_verification_rejects_database_only_receipt(self):
  with tempfile.TemporaryDirectory() as d:
   manifest={'source_url':'https://source.supabase.co','target_url':'https://target.supabase.co','source_commit':'a'*40,'checks':{}}
   with self.assertRaises(ValueError):recovery.verify_service_evidence(Path(d),manifest)
 def test_storage_backup_restore_roundtrip_checks_actual_bytes(self):
  source=b'synthetic-private-object'
  def read(path,body=None,**kwargs):
   if path=='/storage/v1/bucket':return [{'id':'rider-documents','name':'rider-documents','public':False}]
   if '/object/list/' in path:return [{'id':'file','name':'document.pdf','metadata':{'mimetype':'application/pdf'}}]
   return source
  restored={}
  buckets={}
  def write(path,body=None,**kwargs):
   if path=='/storage/v1/bucket':buckets[body['id']]=dict(body);return None
   if path.startswith('/storage/v1/bucket/'):
    bucket=path.split('/')[-1]
    if bucket not in buckets:raise recovery.RecoveryHTTPError(404)
    return buckets[bucket]
   if body is not None:restored[path]=body;return b''
   if path not in restored:raise recovery.RecoveryHTTPError(404)
   return restored[path]
  with tempfile.TemporaryDirectory() as d:
   root=Path(d)/'bundle'
   self.assertEqual(recovery.backup(root,'https://source.supabase.co',read),1)
   import json
   manifest=json.loads((root/'storage-manifest.json').read_text())
   self.assertEqual(recovery.restore(root,manifest,write),1)
   self.assertEqual(next(iter(restored.values())),source)
   self.assertEqual(recovery.restore(root,manifest,write),1)
   buckets['rider-documents']['public']=True
   with self.assertRaises(ValueError):recovery.restore(root,manifest,write)
 def test_restore_rejects_manifest_path_traversal_and_nul(self):
  for name in ['../other','a/../other','bad'+chr(0),'a'+chr(92)+'b']:
   with self.assertRaises(ValueError):recovery.validate_object_record({'bucket':'rider-documents','name':name})
if __name__=='__main__':unittest.main()
