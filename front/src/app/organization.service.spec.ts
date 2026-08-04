import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { OrganizationService, Organization } from './organization.service';
import { Person } from './person.service';
import { API_BASE_URL } from './config';

describe('OrganizationService', () => {
  let service: OrganizationService;
  let httpMock: HttpTestingController;

  const makePerson = (overrides: Partial<Person> = {}): Person => ({
    id: 1,
    firstName: 'Jane',
    lastName: 'Doe',
    email: 'jane@doe.com',
    phone: '0600000000',
    bio: 'bio',
    createdAt: new Date(),
    organizations: [],
    ...overrides,
  });

  const makeOrg = (overrides: Partial<Organization> = {}): Organization => ({
    id: 10,
    name: 'Orion',
    createdAt: new Date(),
    personIds: [],
    persons: [],
    ...overrides,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(OrganizationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('fetchAll should GET /api/organizations and reset persons to []', async () => {
    const orgs = [
      makeOrg({ id: 10, persons: [makePerson()] }), // simule une éventuelle valeur parasite du backend
      makeOrg({ id: 20 }),
    ];

    const promise = service.fetchAll();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/organizations`);
    expect(req.request.method).toBe('GET');
    req.flush(orgs);

    const result = await promise;
    expect(result.length).toBe(2);
    expect(result[0].persons).toEqual([]);
    expect(result[1].persons).toEqual([]);
  });

  it('fetchOrganizationPersons should GET each person id in parallel and clear their organizations', fakeAsync(() => {
    const person1 = makePerson({ id: 1 });
    const person2 = makePerson({ id: 2, organizations: [makeOrg({ id: 99 })] });
    let result!: Person[];

    service.fetchOrganizationPersons([1, 2]).then(r => result = r);

    const req1 = httpMock.expectOne(`${API_BASE_URL}/api/persons/1`);
    const req2 = httpMock.expectOne(`${API_BASE_URL}/api/persons/2`);
    req1.flush(person1);
    req2.flush(person2);
    tick();

    expect(result.length).toBe(2);
    expect(result.find(p => p.id === 1)?.organizations).toEqual([]);
    expect(result.find(p => p.id === 2)?.organizations).toEqual([]);
  }));

  it('fetchOrganizationPersons should resolve to [] without any HTTP call when personIds is empty', async () => {
    const result = await service.fetchOrganizationPersons([]);
    expect(result).toEqual([]);
  });

  it('fetchById should GET the organization then hydrate its persons', fakeAsync(() => {
    const org = makeOrg({ id: 10, personIds: [1] });
    const person = makePerson({ id: 1 });
    let result!: Organization;

    service.fetchById(10).then(r => result = r);

    const orgReq = httpMock.expectOne(`${API_BASE_URL}/api/organizations/10`);
    expect(orgReq.request.method).toBe('GET');
    orgReq.flush(org);
    tick();

    const personReq = httpMock.expectOne(`${API_BASE_URL}/api/persons/1`);
    personReq.flush(person);
    tick();

    expect(result.persons.length).toBe(1);
    expect(result.persons[0].id).toBe(1);
  }));

  it('deleteById should DELETE /api/organizations/:id', async () => {
    const promise = service.deleteById(10);

    const req = httpMock.expectOne(`${API_BASE_URL}/api/organizations/10`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    await expectAsync(promise).toBeResolved();
  });

  it('save should POST when the organization has no id, then hydrate persons', fakeAsync(() => {
    const newOrg = makeOrg({ id: undefined as unknown as number, personIds: [] });
    const created = makeOrg({ id: 42, personIds: [] });
    let result!: Organization;

    service.save(newOrg).then(r => result = r);

    const postReq = httpMock.expectOne(`${API_BASE_URL}/api/organizations`);
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ name: newOrg.name });
    postReq.flush(created);
    tick();

    expect(result.id).toBe(42);
    expect(result.persons).toEqual([]);
  }));

  it('save should PUT when the organization already has an id', fakeAsync(() => {
    const existing = makeOrg({ id: 7, personIds: [] });

    service.save(existing).then();

    const putReq = httpMock.expectOne(`${API_BASE_URL}/api/organizations/7`);
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body).toEqual({ name: existing.name });
    putReq.flush(existing);
    tick();
  }));

  it('addPerson should POST with no body to /api/organizations/:orgId/persons/:personId', async () => {
    const promise = service.addPerson(10, 1);

    const req = httpMock.expectOne(`${API_BASE_URL}/api/organizations/10/persons/1`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush(makeOrg());

    await expectAsync(promise).toBeResolved();
  });

  it('removePerson should DELETE /api/organizations/:orgId/persons/:personId', async () => {
    const promise = service.removePerson(10, 1);

    const req = httpMock.expectOne(`${API_BASE_URL}/api/organizations/10/persons/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(makeOrg());

    await expectAsync(promise).toBeResolved();
  });
});