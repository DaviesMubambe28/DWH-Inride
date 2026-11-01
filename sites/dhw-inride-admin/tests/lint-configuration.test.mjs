import {test} from 'node:test';
import assert from 'node:assert/strict';
import {ESLint} from 'eslint';
test('security-patched glob matching still includes TSX and ignores generated output',async()=>{
 const eslint=new ESLint();
 assert.equal(await eslint.isPathIgnored('components/admin-dashboard.tsx'),false);
 assert.equal(await eslint.isPathIgnored('out/index.html'),true);
 assert.equal(await eslint.isPathIgnored('.next/types/routes.d.ts'),true);
 const [result]=await eslint.lintText('export default function Example(){ return <img src="/test.png" />; }',{filePath:'components/security-fixture.tsx'});
 assert.ok(result.messages.some(message=>message.ruleId==='jsx-a11y/alt-text'),'accessibility rules must still run');
});
